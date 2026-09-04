<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoInput, WegoPageHeader, WegoPagination, WegoPanel, WegoSelect } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  type BoatCharter,
  closeOffering,
  createOffering,
  DiversApiError,
  getOfferingBoatCharter,
  linkOfferingBoatCharter,
  listBoatCharters,
  listOfferings,
  type Offering,
  type OfferingBoatCharterLink,
  type OfferingType,
  PAGE_SIZE,
  type PricingBasis,
  unlinkOfferingBoatCharter,
} from "../composables/useDiversApi";

definePageMeta({ layout: "app-shell" });

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
const canManageCharter = () => hasPermission(session.value, "boat-charter:manage");
const canViewCharter = () => hasPermission(session.value, "boat-charter:view");

// Boat-charter linking, expanded per offering on demand rather than
// eagerly for every row — a page full of non-boat offerings shouldn't
// fire an extra request per row just to find out none of them are linked.
const expandedCharterOfferingId = ref<string | null>(null);
const activeCharters = ref<BoatCharter[]>([]);
const charterLinks = ref<Record<string, OfferingBoatCharterLink | null>>({});
const charterState = ref<Record<string, "idle" | "loading" | "submitting" | "error">>({});
const charterError = ref<Record<string, string>>({});
const selectedCharterId = ref<Record<string, string>>({});

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
    if (error.errorCode === "charter_not_active") return "That charter has ended.";
    if (error.errorCode === "offering_has_no_capacity") return "Set a capacity on this offering before linking a charter.";
    if (error.errorCode === "offering_capacity_exceeds_charter") return "This offering's capacity is more than that boat is licensed for.";
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

// Guards against a real race: a page-load fetch and a create/close can
// resolve out of order. Without this, a slow initial GET (e.g. 50 seeded
// offerings under CI load) that started before a fast create's response
// can arrive *after* it and silently overwrite the optimistically-updated
// list with the pre-create snapshot, making the just-created offering
// disappear. Every write to `offerings.value` bumps this counter; a load
// result is only applied if the counter hasn't moved since that load
// started.
let requestGeneration = 0;

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
  const generation = ++requestGeneration;
  listState.value = "loading";
  listError.value = "";
  try {
    const result = await listOfferings(session.value.token, { page: page.value });
    // A stale response (a create/close/newer load happened while this was
    // in flight) must not overwrite newer data — but it must still clear
    // "loading", or the page gets stuck showing "Loading…" forever even
    // though offerings.value already has the right content.
    if (generation === requestGeneration) {
      offerings.value = result;
      hasNextPage.value = result.length === PAGE_SIZE;
    }
    listState.value = "loaded";
  } catch (error) {
    if (generation === requestGeneration) {
      handleApiError(error);
      listError.value = errorText(error);
    }
    listState.value = "error";
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
    requestGeneration++;
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
    requestGeneration++;
    offerings.value = offerings.value.map((existing) => (existing.id === updated.id ? updated : existing));
    closeState.value[offering.id] = "idle";
  } catch (error) {
    handleApiError(error);
    closeState.value[offering.id] = "error";
    closeError.value[offering.id] = errorText(error);
  }
}

async function toggleCharterPanel(offering: Offering) {
  if (expandedCharterOfferingId.value === offering.id) {
    expandedCharterOfferingId.value = null;
    return;
  }
  expandedCharterOfferingId.value = offering.id;
  if (!session.value) return;

  charterState.value[offering.id] = "loading";
  charterError.value[offering.id] = "";
  try {
    if (activeCharters.value.length === 0 && canManageCharter()) {
      activeCharters.value = await listBoatCharters(session.value.token, { status: "ACTIVE" });
    }
    charterLinks.value[offering.id] = await getOfferingBoatCharter(session.value.token, offering.id);
    charterState.value[offering.id] = "idle";
  } catch (error) {
    handleApiError(error);
    charterState.value[offering.id] = "error";
    charterError.value[offering.id] = errorText(error);
  }
}

async function submitLinkCharter(offering: Offering) {
  if (!session.value) return;
  const boatCharterId = selectedCharterId.value[offering.id];
  if (!boatCharterId) return;

  charterState.value[offering.id] = "submitting";
  charterError.value[offering.id] = "";
  try {
    charterLinks.value[offering.id] = await linkOfferingBoatCharter(session.value.token, offering.id, boatCharterId);
    charterState.value[offering.id] = "idle";
  } catch (error) {
    handleApiError(error);
    charterState.value[offering.id] = "error";
    charterError.value[offering.id] = errorText(error);
  }
}

async function submitUnlinkCharter(offering: Offering) {
  if (!session.value) return;
  charterState.value[offering.id] = "submitting";
  charterError.value[offering.id] = "";
  try {
    await unlinkOfferingBoatCharter(session.value.token, offering.id);
    charterLinks.value[offering.id] = null;
    charterState.value[offering.id] = "idle";
  } catch (error) {
    handleApiError(error);
    charterState.value[offering.id] = "error";
    charterError.value[offering.id] = errorText(error);
  }
}

function charterNameFor(boatCharterId: string): string {
  return activeCharters.value.find((charter) => charter.id === boatCharterId)?.boatName ?? boatCharterId;
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadOfferings();
});
</script>

<template>
  <WegoPageHeader title="Offerings" description="Dive trips, courses, rentals, and packages available to book." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view offerings.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel title="Offerings" class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to list offerings (offering:view).
      </p>
      <p v-else-if="listState === 'loading'" class="text-sm text-wego-muted">Loading…</p>
      <p v-else-if="listState === 'loaded' && offerings.length === 0 && page === 0" class="text-sm text-wego-muted">
        No offerings yet.
      </p>
      <ul v-else-if="offerings.length > 0" class="space-y-3">
        <li v-for="offering in offerings" :key="offering.id" class="rounded-wego-control border border-wego-border p-4">
          <div class="flex items-start justify-between gap-3">
            <p class="font-semibold">{{ offering.title }}</p>
            <WegoBadge :tone="offering.status === 'ACTIVE' ? 'success' : 'neutral'">{{ offering.status }}</WegoBadge>
          </div>
          <p class="mt-1 text-sm text-wego-muted">
            {{ offering.offeringType }} · {{ offering.startsOn }}<span v-if="offering.endsOn"> – {{ offering.endsOn }}</span>
            · {{ offering.unitPrice.amount }} {{ offering.unitPrice.currencyCode }}
            {{ offering.pricingBasis === "PER_PARTICIPANT" ? "per participant" : "flat" }} · capacity
            {{ offering.capacity ?? "unlimited" }}
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

          <div v-if="canViewCharter() && offering.capacity" class="mt-3">
            <WegoButton type="button" variant="ghost" @click="toggleCharterPanel(offering)">
              {{ expandedCharterOfferingId === offering.id ? "Hide boat charter" : "Boat charter" }}
            </WegoButton>

            <div v-if="expandedCharterOfferingId === offering.id" class="mt-3 rounded-wego-control border border-wego-border p-3">
              <p v-if="charterState[offering.id] === 'loading'" class="text-sm text-wego-muted">Loading…</p>
              <template v-else>
                <p v-if="charterLinks[offering.id]" class="text-sm">
                  Linked to <strong>{{ charterNameFor(charterLinks[offering.id]!.boatCharterId) }}</strong>
                </p>
                <p v-else class="text-sm text-wego-muted">Not linked to a boat charter.</p>

                <WegoAlert v-if="charterState[offering.id] === 'error'" variant="danger" class="mt-2">
                  {{ charterError[offering.id] }}
                </WegoAlert>

                <div v-if="canManageCharter()" class="mt-3 flex flex-wrap items-end gap-2">
                  <WegoButton
                    v-if="charterLinks[offering.id]"
                    type="button"
                    variant="secondary"
                    :disabled="charterState[offering.id] === 'submitting'"
                    @click="submitUnlinkCharter(offering)"
                  >
                    Unlink
                  </WegoButton>
                  <template v-else>
                    <WegoSelect
                      :id="`charter-select-${offering.id}`"
                      :model-value="selectedCharterId[offering.id] ?? ''"
                      label="Link to"
                      @update:model-value="(value) => (selectedCharterId[offering.id] = value)"
                    >
                      <option value="" disabled>Select a charter…</option>
                      <option v-for="charter in activeCharters" :key="charter.id" :value="charter.id">
                        {{ charter.boatName }} (licensed {{ charter.licensedCapacity }})
                      </option>
                    </WegoSelect>
                    <WegoButton
                      type="button"
                      variant="secondary"
                      :disabled="charterState[offering.id] === 'submitting' || !selectedCharterId[offering.id]"
                      @click="submitLinkCharter(offering)"
                    >
                      Link
                    </WegoButton>
                  </template>
                </div>
              </template>
            </div>
          </div>
        </li>
      </ul>

      <WegoPagination
        v-if="canView() && listState === 'loaded'"
        class="mt-4"
        :page="page"
        :has-next-page="hasNextPage"
        @previous="previousPage"
        @next="nextPage"
      />
    </WegoPanel>

    <WegoPanel v-if="canManage()" title="New offering" class="mt-8">
      <form class="space-y-5" @submit.prevent="submitCreate">
        <WegoSelect id="offeringType" v-model="form.offeringType" label="Type">
          <option v-for="type in offeringTypes" :key="type" :value="type">{{ type }}</option>
        </WegoSelect>
        <WegoInput id="title" v-model="form.title" label="Title" required />
        <WegoInput id="description" v-model="form.description" label="Description (optional)" />
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="startsOn" v-model="form.startsOn" label="Starts on" type="date" required />
          <WegoInput id="endsOn" v-model="form.endsOn" label="Ends on (optional)" type="date" />
        </div>
        <WegoSelect id="pricingBasis" v-model="form.pricingBasis" label="Pricing basis">
          <option v-for="basis in pricingBases" :key="basis" :value="basis">
            {{ basis === "PER_PARTICIPANT" ? "Per participant" : "Flat (whole booking)" }}
          </option>
        </WegoSelect>
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
    </WegoPanel>
  </template>
</template>
