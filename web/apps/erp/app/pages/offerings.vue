<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  closeOffering,
  createOffering,
  DiversApiError,
  listOfferings,
  type Offering,
  type OfferingType,
  type PricingBasis,
  PAGE_SIZE,
} from "../composables/useDiversApi";

useHead({ title: "Offerings · Wego Platform" });

const session = ref<AuthSession | null>(null);
const offerings = ref<Offering[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const page = ref(0);
// The API returns a plain page, not a total count — a full page (size ===
// PAGE_SIZE) means there might be more, so "Next" stays enabled until a
// short page proves we've reached the end. This is what keeps a Previous
// booking or offering from being silently hidden past the first page.
const hasNextPage = ref(false);

const offeringTypes: OfferingType[] = ["DIVE_TRIP", "COURSE", "EQUIPMENT_RENTAL", "PACKAGE"];
const pricingBases: PricingBasis[] = ["PER_PARTICIPANT", "FLAT"];

const form = ref({
  offeringType: "DIVE_TRIP" as OfferingType,
  title: "",
  description: "",
  startsOn: "",
  endsOn: "",
  capacity: "",
  pricingBasis: "PER_PARTICIPANT" as PricingBasis,
  amount: "",
  currencyCode: "EUR",
});
const createState = ref<"idle" | "submitting" | "error">("idle");
const createError = ref("");

// Per-offering in-flight close action, keyed by offering id.
const closeState = ref<Record<string, "idle" | "submitting" | "error">>({});
const closeError = ref<Record<string, string>>({});
const closeReason = ref<Record<string, string>>({});

const canManage = () => hasPermission(session.value, "offering:manage");
const canView = () => hasPermission(session.value, "offering:view");

// A 401 means the persisted session is no longer valid server-side
// (expired/revoked elsewhere) — drop it locally too, so the page falls
// back to the sign-in prompt instead of repeatedly retrying requests that
// can only ever fail the same way.
function handleApiError(error: unknown) {
  if (error instanceof DiversApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof DiversApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "already_closed") return "That offering is already closed.";
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadOfferings() {
  if (!session.value) return;
  // The create permission is intentionally distinct from the read
  // permission. A manage-only session may still use the create form, but
  // the page must not issue a list request the backend will necessarily
  // reject with 403.
  if (!canView()) {
    offerings.value = [];
    hasNextPage.value = false;
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const result = await listOfferings(session.value.token, { page: page.value });
    offerings.value = result;
    hasNextPage.value = result.length === PAGE_SIZE;
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function nextPage() {
  page.value += 1;
  loadOfferings();
}

function previousPage() {
  if (page.value === 0) return;
  page.value -= 1;
  loadOfferings();
}

async function submitCreate() {
  if (!session.value) return;
  createState.value = "submitting";
  createError.value = "";
  try {
    const created = await createOffering(session.value.token, {
      offeringType: form.value.offeringType,
      title: form.value.title,
      description: form.value.description || undefined,
      startsOn: form.value.startsOn,
      endsOn: form.value.endsOn || undefined,
      capacity: form.value.capacity ? Number(form.value.capacity) : undefined,
      pricingBasis: form.value.pricingBasis,
      unitPrice: { amount: form.value.amount, currencyCode: form.value.currencyCode },
    });
    offerings.value = [created, ...offerings.value];
    form.value = {
      offeringType: "DIVE_TRIP",
      title: "",
      description: "",
      startsOn: "",
      endsOn: "",
      capacity: "",
      pricingBasis: "PER_PARTICIPANT",
      amount: "",
      currencyCode: "EUR",
    };
    createState.value = "idle";
  } catch (error) {
    handleApiError(error);
    createState.value = "error";
    createError.value = errorText(error);
  }
}

async function submitClose(offering: Offering) {
  if (!session.value) return;
  const reason = closeReason.value[offering.id] ?? "";
  // Confirmation dialog for a terminal, irreversible action — matches the
  // same pattern the bookings page uses for cancel/refund.
  if (!window.confirm(`Close "${offering.title}" to further bookings? This cannot be undone.`)) return;

  closeState.value[offering.id] = "submitting";
  closeError.value[offering.id] = "";
  try {
    const updated = await closeOffering(session.value.token, offering.id, reason);
    offerings.value = offerings.value.map((existing) => (existing.id === updated.id ? updated : existing));
    closeState.value[offering.id] = "idle";
  } catch (error) {
    handleApiError(error);
    closeState.value[offering.id] = "error";
    closeError.value[offering.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadOfferings();
});
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Wego Platform</p>
      <h1 class="mt-4 text-3xl font-semibold tracking-tight">Offerings</h1>

      <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
        <p>You need to sign in to view offerings.</p>
        <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
      </div>

      <template v-else>
        <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

        <section class="mt-8">
          <h2 class="text-xl font-semibold">Offerings</h2>
          <p v-if="!canView()" class="mt-3 text-sm text-wego-muted">
            Your account doesn't have permission to list offerings (offering:view).
          </p>
          <p v-else-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
          <p v-else-if="listState === 'loaded' && offerings.length === 0 && page === 0" class="mt-3 text-sm text-wego-muted">
            No offerings yet.
          </p>
          <ul v-else-if="offerings.length > 0" class="mt-4 space-y-3">
            <li
              v-for="offering in offerings"
              :key="offering.id"
              class="rounded-wego-card border border-wego-border bg-wego-surface p-4"
            >
              <p class="font-semibold">{{ offering.title }}</p>
              <p class="mt-1 text-sm text-wego-muted">
                {{ offering.offeringType }} · {{ offering.startsOn }}<span v-if="offering.endsOn">
                  – {{ offering.endsOn }}</span
                >
                · {{ offering.unitPrice.amount }} {{ offering.unitPrice.currencyCode }}
                {{ offering.pricingBasis === "PER_PARTICIPANT" ? "per participant" : "flat" }} · capacity
                {{ offering.capacity ?? "unlimited" }} · {{ offering.status }}
              </p>

              <WegoAlert v-if="closeState[offering.id] === 'error'" variant="danger" class="mt-2">
                {{ closeError[offering.id] }}
              </WegoAlert>

              <div v-if="canManage() && offering.status === 'ACTIVE'" class="mt-3 flex flex-wrap items-end gap-2">
                <WegoInput
                  :id="`close-reason-${offering.id}`"
                  :model-value="closeReason[offering.id] ?? ''"
                  label="Close reason (optional)"
                  class="min-w-0 flex-1"
                  @update:model-value="(value) => (closeReason[offering.id] = value)"
                />
                <WegoButton
                  type="button"
                  variant="secondary"
                  :disabled="closeState[offering.id] === 'submitting'"
                  @click="submitClose(offering)"
                >
                  Close offering
                </WegoButton>
              </div>
            </li>
          </ul>

          <div v-if="canView() && listState === 'loaded'" class="mt-4 flex items-center gap-3">
            <WegoButton type="button" variant="secondary" :disabled="page === 0" @click="previousPage">Previous</WegoButton>
            <span class="text-sm text-wego-muted">Page {{ page + 1 }}</span>
            <WegoButton type="button" variant="secondary" :disabled="!hasNextPage" @click="nextPage">Next</WegoButton>
          </div>
        </section>

        <section
          v-if="canManage()"
          class="mt-10 rounded-wego-card border border-wego-border bg-wego-surface p-6"
        >
          <h2 class="text-xl font-semibold">New offering</h2>
          <form class="mt-6 space-y-5" @submit.prevent="submitCreate">
            <div>
              <label for="offeringType" class="block text-sm font-medium text-wego-muted">Type</label>
              <select
                id="offeringType"
                v-model="form.offeringType"
                class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
              >
                <option v-for="type in offeringTypes" :key="type" :value="type">{{ type }}</option>
              </select>
            </div>
            <WegoInput id="title" v-model="form.title" label="Title" required />
            <WegoInput id="description" v-model="form.description" label="Description (optional)" />
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="startsOn" v-model="form.startsOn" label="Starts on" type="date" required />
              <WegoInput id="endsOn" v-model="form.endsOn" label="Ends on (optional)" type="date" />
            </div>
            <div>
              <label for="pricingBasis" class="block text-sm font-medium text-wego-muted">Pricing basis</label>
              <select
                id="pricingBasis"
                v-model="form.pricingBasis"
                class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
              >
                <option v-for="basis in pricingBases" :key="basis" :value="basis">
                  {{ basis === "PER_PARTICIPANT" ? "Per participant" : "Flat (whole booking)" }}
                </option>
              </select>
            </div>
            <div class="grid gap-5 sm:grid-cols-3">
              <WegoInput id="capacity" v-model="form.capacity" label="Capacity (optional)" type="number" />
              <WegoInput id="amount" v-model="form.amount" label="Price amount" required />
              <WegoInput id="currencyCode" v-model="form.currencyCode" label="Currency" required />
            </div>

            <WegoAlert v-if="createState === 'error'" variant="danger">{{ createError }}</WegoAlert>

            <WegoButton type="submit" :disabled="createState === 'submitting'" :loading="createState === 'submitting'">
              {{ createState === "submitting" ? "Creating…" : "Create offering" }}
            </WegoButton>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
