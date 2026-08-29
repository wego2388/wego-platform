<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  type BoatCharter,
  type CharterStatus,
  type CharterType,
  createBoatCharter,
  DiversApiError,
  endBoatCharter,
  listBoatCharters,
  updateBoatCharter,
} from "../composables/useDiversApi";

useHead({ title: "Boat Charters · Wego Platform" });

const session = ref<AuthSession | null>(null);
const charters = ref<BoatCharter[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const statusFilter = ref<CharterStatus | "">("ACTIVE");
const search = ref("");

const charterTypes: CharterType[] = ["STANDING", "DAILY", "SAFARI"];

function blankForm() {
  return { boatName: "", charterType: "STANDING" as CharterType, licensedCapacity: "", startsOn: "", endsOn: "", notes: "" };
}

const form = ref(blankForm());
const editingCharterId = ref<string | null>(null);
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

const actionState = ref<Record<string, "idle" | "submitting" | "error">>({});
const actionError = ref<Record<string, string>>({});

const canManage = () => hasPermission(session.value, "boat-charter:manage");
const canView = () => hasPermission(session.value, "boat-charter:view");

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
    if (error.errorCode === "already_ended") return "That charter has already ended.";
    if (error.errorCode === "capacity_below_linked_offerings") {
      return "That capacity is lower than a trip that's already linked to this boat — adjust or unlink that trip first.";
    }
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadCharters() {
  if (!session.value || !canView()) {
    charters.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    charters.value = await listBoatCharters(session.value.token, { status: statusFilter.value || undefined, search: search.value || undefined });
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function runSearch() {
  loadCharters();
}

function startEdit(charter: BoatCharter) {
  editingCharterId.value = charter.id;
  form.value = {
    boatName: charter.boatName,
    charterType: charter.charterType,
    licensedCapacity: String(charter.licensedCapacity),
    startsOn: charter.startsOn,
    endsOn: charter.endsOn ?? "",
    notes: charter.notes ?? "",
  };
  formState.value = "idle";
  formError.value = "";
}

function cancelEdit() {
  editingCharterId.value = null;
  form.value = blankForm();
  formState.value = "idle";
  formError.value = "";
}

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    if (editingCharterId.value) {
      const updated = await updateBoatCharter(session.value.token, editingCharterId.value, {
        boatName: form.value.boatName,
        licensedCapacity: Number(form.value.licensedCapacity),
        startsOn: form.value.startsOn,
        endsOn: form.value.endsOn || undefined,
        notes: form.value.notes || undefined,
      });
      charters.value = charters.value.map((existing) => (existing.id === updated.id ? updated : existing));
    } else {
      const created = await createBoatCharter(session.value.token, {
        boatName: form.value.boatName,
        charterType: form.value.charterType,
        licensedCapacity: Number(form.value.licensedCapacity),
        startsOn: form.value.startsOn,
        endsOn: form.value.endsOn || undefined,
        notes: form.value.notes || undefined,
      });
      charters.value = [created, ...charters.value];
    }
    cancelEdit();
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

async function submitEnd(charter: BoatCharter) {
  if (!session.value) return;
  if (!window.confirm(`End the charter for "${charter.boatName}"? This cannot be undone.`)) return;

  actionState.value[charter.id] = "submitting";
  actionError.value[charter.id] = "";
  try {
    const updated = await endBoatCharter(session.value.token, charter.id);
    charters.value = charters.value.map((existing) => (existing.id === updated.id ? updated : existing));
    actionState.value[charter.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[charter.id] = "error";
    actionError.value[charter.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadCharters();
});
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Wego Platform</p>
      <h1 class="mt-4 text-3xl font-semibold tracking-tight">Boat Charters</h1>
      <p class="mt-2 text-sm text-wego-muted">
        Real chartered boats, not an owned fleet. Link a boat trip to a charter on the Offerings page to enforce its licensed capacity.
      </p>

      <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
        <p>You need to sign in to view boat charters.</p>
        <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
      </div>

      <template v-else>
        <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

        <section class="mt-8">
          <h2 class="text-xl font-semibold">Charter agreements</h2>
          <p v-if="!canView()" class="mt-3 text-sm text-wego-muted">
            Your account doesn't have permission to view boat charters (boat-charter:view).
          </p>
          <template v-else>
            <div class="mt-4 flex flex-wrap items-end gap-3">
              <WegoInput id="search" v-model="search" label="Search boat name" class="min-w-0 flex-1" @keyup.enter="runSearch" />
              <div>
                <label for="statusFilter" class="block text-sm font-medium text-wego-muted">Status</label>
                <select
                  id="statusFilter"
                  v-model="statusFilter"
                  class="mt-2 rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
                  @change="runSearch"
                >
                  <option value="ACTIVE">Active</option>
                  <option value="ENDED">Ended</option>
                  <option value="">All</option>
                </select>
              </div>
              <WegoButton type="button" variant="secondary" @click="runSearch">Search</WegoButton>
            </div>

            <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
            <p v-else-if="listState === 'loaded' && charters.length === 0" class="mt-3 text-sm text-wego-muted">No charters yet.</p>
            <ul v-else class="mt-4 space-y-3">
              <li v-for="charter in charters" :key="charter.id" class="rounded-wego-card border border-wego-border bg-wego-surface p-4">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p class="font-semibold">{{ charter.boatName }}</p>
                    <p class="mt-1 text-sm text-wego-muted">
                      {{ charter.charterType }} · licensed for {{ charter.licensedCapacity }} passengers · from {{ charter.startsOn
                      }}<span v-if="charter.endsOn"> to {{ charter.endsOn }}</span> · {{ charter.status }}
                    </p>
                    <p v-if="charter.notes" class="mt-1 text-sm text-wego-muted">{{ charter.notes }}</p>
                  </div>
                  <div v-if="canManage()" class="flex shrink-0 gap-2">
                    <WegoButton type="button" variant="secondary" @click="startEdit(charter)">Edit</WegoButton>
                    <WegoButton
                      v-if="charter.status === 'ACTIVE'"
                      type="button"
                      variant="secondary"
                      :disabled="actionState[charter.id] === 'submitting'"
                      @click="submitEnd(charter)"
                    >
                      End charter
                    </WegoButton>
                  </div>
                </div>
                <WegoAlert v-if="actionState[charter.id] === 'error'" variant="danger" class="mt-2">{{ actionError[charter.id] }}</WegoAlert>
              </li>
            </ul>
          </template>
        </section>

        <section v-if="canManage()" class="mt-10 rounded-wego-card border border-wego-border bg-wego-surface p-6">
          <h2 class="text-xl font-semibold">{{ editingCharterId ? "Edit charter" : "New charter" }}</h2>
          <form class="mt-6 space-y-5" @submit.prevent="submitForm">
            <WegoInput id="boatName" v-model="form.boatName" label="Boat name" required />
            <div>
              <label for="charterType" class="block text-sm font-medium text-wego-muted">Charter type</label>
              <select
                id="charterType"
                v-model="form.charterType"
                :disabled="editingCharterId !== null"
                class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
              >
                <option v-for="type in charterTypes" :key="type" :value="type">{{ type }}</option>
              </select>
            </div>
            <WegoInput id="licensedCapacity" v-model="form.licensedCapacity" label="Licensed passenger capacity" type="number" required />
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="startsOn" v-model="form.startsOn" label="Starts on" type="date" required />
              <WegoInput id="endsOn" v-model="form.endsOn" label="Ends on (optional)" type="date" />
            </div>
            <WegoInput id="notes" v-model="form.notes" label="Notes (optional)" />

            <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

            <div class="flex gap-3">
              <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
                {{ editingCharterId ? "Save changes" : "Register charter" }}
              </WegoButton>
              <WegoButton v-if="editingCharterId" type="button" variant="secondary" @click="cancelEdit">Cancel</WegoButton>
            </div>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
