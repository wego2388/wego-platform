<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  archiveDiver,
  createDiver,
  type Diver,
  type DiverCertification,
  DiversApiError,
  type DiverStatus,
  listDivers,
  PAGE_SIZE,
  updateDiver,
  type UpsertDiverBody,
} from "../composables/useDiversApi";

useHead({ title: "Divers · Wego Platform" });

const session = ref<AuthSession | null>(null);
const divers = ref<Diver[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const page = ref(0);
const hasNextPage = ref(false);
const statusFilter = ref<DiverStatus | "">("ACTIVE");
const search = ref("");

function blankForm() {
  return {
    fullName: "",
    nationality: "",
    primaryLanguage: "",
    email: "",
    phone: "",
    emergencyContactName: "",
    emergencyContactPhone: "",
    medicalNotes: "",
    totalLoggedDives: "0",
    maxDepthMeters: "",
    lastDiveOn: "",
    bcdSize: "",
    finSize: "",
    wetsuitSize: "",
    certifications: [] as DiverCertification[],
  };
}

const form = ref(blankForm());
// null while creating a new profile; the diver's id while editing an existing one.
const editingDiverId = ref<string | null>(null);
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

const archiveState = ref<Record<string, "idle" | "submitting" | "error">>({});
const archiveError = ref<Record<string, string>>({});

const canManage = () => hasPermission(session.value, "diver:manage");
const canView = () => hasPermission(session.value, "diver:view");

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
    if (error.errorCode === "already_archived") return "That diver profile is already archived.";
    if (error.status === 404) return "Not found.";
    if (error.status === 400) return "Check the form — one of the fields isn't valid (a diver needs an email or a phone number).";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadDivers() {
  if (!session.value) return;
  if (!canView()) {
    divers.value = [];
    hasNextPage.value = false;
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const result = await listDivers(session.value.token, {
      status: statusFilter.value || undefined,
      search: search.value || undefined,
      page: page.value,
    });
    divers.value = result;
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
  loadDivers();
}

function previousPage() {
  if (page.value === 0) return;
  page.value -= 1;
  loadDivers();
}

function runSearch() {
  page.value = 0;
  loadDivers();
}

function addCertificationRow() {
  form.value.certifications = [...form.value.certifications, { agency: "", level: "", certificationNumber: "", issuedOn: "" }];
}

function removeCertificationRow(index: number) {
  form.value.certifications = form.value.certifications.filter((_, i) => i !== index);
}

function startEdit(diver: Diver) {
  editingDiverId.value = diver.id;
  form.value = {
    fullName: diver.fullName,
    nationality: diver.nationality ?? "",
    primaryLanguage: diver.primaryLanguage ?? "",
    email: diver.email ?? "",
    phone: diver.phone ?? "",
    emergencyContactName: diver.emergencyContactName ?? "",
    emergencyContactPhone: diver.emergencyContactPhone ?? "",
    medicalNotes: diver.medicalNotes ?? "",
    totalLoggedDives: String(diver.totalLoggedDives),
    maxDepthMeters: diver.maxDepthMeters ?? "",
    lastDiveOn: diver.lastDiveOn ?? "",
    bcdSize: diver.bcdSize ?? "",
    finSize: diver.finSize ?? "",
    wetsuitSize: diver.wetsuitSize ?? "",
    certifications: diver.certifications.map((certification) => ({
      ...certification,
      certificationNumber: certification.certificationNumber ?? "",
      issuedOn: certification.issuedOn ?? "",
    })),
  };
  formState.value = "idle";
  formError.value = "";
  window.scrollTo({ top: document.body.scrollHeight, behavior: "smooth" });
}

function cancelEdit() {
  editingDiverId.value = null;
  form.value = blankForm();
  formState.value = "idle";
  formError.value = "";
}

function buildRequestBody(): UpsertDiverBody {
  return {
    fullName: form.value.fullName,
    nationality: form.value.nationality || undefined,
    primaryLanguage: form.value.primaryLanguage || undefined,
    email: form.value.email || undefined,
    phone: form.value.phone || undefined,
    emergencyContactName: form.value.emergencyContactName || undefined,
    emergencyContactPhone: form.value.emergencyContactPhone || undefined,
    medicalNotes: form.value.medicalNotes || undefined,
    totalLoggedDives: Number(form.value.totalLoggedDives || 0),
    maxDepthMeters: form.value.maxDepthMeters ? Number(form.value.maxDepthMeters) : undefined,
    lastDiveOn: form.value.lastDiveOn || undefined,
    bcdSize: form.value.bcdSize || undefined,
    finSize: form.value.finSize || undefined,
    wetsuitSize: form.value.wetsuitSize || undefined,
    certifications: form.value.certifications.filter((certification) => certification.agency && certification.level),
  };
}

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    const body = buildRequestBody();
    if (editingDiverId.value) {
      const updated = await updateDiver(session.value.token, editingDiverId.value, body);
      divers.value = divers.value.map((existing) => (existing.id === updated.id ? updated : existing));
    } else {
      const created = await createDiver(session.value.token, body);
      divers.value = [created, ...divers.value];
    }
    cancelEdit();
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

async function submitArchive(diver: Diver) {
  if (!session.value) return;
  if (!window.confirm(`Archive "${diver.fullName}"'s profile? It stops appearing in the active list, but nothing is deleted.`)) return;

  archiveState.value[diver.id] = "submitting";
  archiveError.value[diver.id] = "";
  try {
    const updated = await archiveDiver(session.value.token, diver.id);
    if (statusFilter.value === "ACTIVE") {
      divers.value = divers.value.filter((existing) => existing.id !== updated.id);
    } else {
      divers.value = divers.value.map((existing) => (existing.id === updated.id ? updated : existing));
    }
    archiveState.value[diver.id] = "idle";
  } catch (error) {
    handleApiError(error);
    archiveState.value[diver.id] = "error";
    archiveError.value[diver.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadDivers();
});
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Wego Platform</p>
      <h1 class="mt-4 text-3xl font-semibold tracking-tight">Divers</h1>

      <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
        <p>You need to sign in to view diver profiles.</p>
        <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
      </div>

      <template v-else>
        <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

        <section class="mt-8">
          <h2 class="text-xl font-semibold">Diver profiles</h2>
          <p v-if="!canView()" class="mt-3 text-sm text-wego-muted">
            Your account doesn't have permission to list divers (diver:view).
          </p>
          <template v-else>
            <div class="mt-4 flex flex-wrap items-end gap-3">
              <WegoInput id="search" v-model="search" label="Search by name" class="min-w-0 flex-1" @keyup.enter="runSearch" />
              <div>
                <label for="statusFilter" class="block text-sm font-medium text-wego-muted">Status</label>
                <select
                  id="statusFilter"
                  v-model="statusFilter"
                  class="mt-2 rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
                  @change="runSearch"
                >
                  <option value="ACTIVE">Active</option>
                  <option value="ARCHIVED">Archived</option>
                  <option value="">All</option>
                </select>
              </div>
              <WegoButton type="button" variant="secondary" @click="runSearch">Search</WegoButton>
            </div>

            <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
            <p v-else-if="listState === 'loaded' && divers.length === 0 && page === 0" class="mt-3 text-sm text-wego-muted">
              No diver profiles yet.
            </p>
            <ul v-else-if="divers.length > 0" class="mt-4 space-y-3">
              <li v-for="diver in divers" :key="diver.id" class="rounded-wego-card border border-wego-border bg-wego-surface p-4">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p class="font-semibold">{{ diver.fullName }}</p>
                    <p class="mt-1 text-sm text-wego-muted">
                      <span v-if="diver.nationality">{{ diver.nationality }} · </span>
                      <span v-if="diver.primaryLanguage">{{ diver.primaryLanguage }} · </span>
                      {{ diver.email || diver.phone || "no contact on file" }}
                    </p>
                    <p class="mt-1 text-sm text-wego-muted">
                      {{ diver.totalLoggedDives }} logged dives<span v-if="diver.maxDepthMeters">
                        · max {{ diver.maxDepthMeters }}m</span
                      ><span v-if="diver.lastDiveOn"> · last dive {{ diver.lastDiveOn }}</span> · {{ diver.status }}
                    </p>
                    <p v-if="diver.certifications.length > 0" class="mt-2 flex flex-wrap gap-2">
                      <span
                        v-for="certification in diver.certifications"
                        :key="certification.id ?? `${certification.agency}-${certification.level}`"
                        class="rounded-full bg-wego-canvas px-3 py-1 text-xs font-medium text-wego-muted"
                      >
                        {{ certification.agency }} · {{ certification.level }}
                      </span>
                    </p>
                  </div>
                  <div v-if="canManage()" class="flex shrink-0 gap-2">
                    <WegoButton type="button" variant="secondary" @click="startEdit(diver)">Edit</WegoButton>
                    <WegoButton
                      v-if="diver.status === 'ACTIVE'"
                      type="button"
                      variant="secondary"
                      :disabled="archiveState[diver.id] === 'submitting'"
                      @click="submitArchive(diver)"
                    >
                      Archive
                    </WegoButton>
                  </div>
                </div>
                <WegoAlert v-if="archiveState[diver.id] === 'error'" variant="danger" class="mt-2">
                  {{ archiveError[diver.id] }}
                </WegoAlert>
              </li>
            </ul>

            <div class="mt-4 flex items-center gap-3">
              <WegoButton type="button" variant="secondary" :disabled="page === 0" @click="previousPage">Previous</WegoButton>
              <span class="text-sm text-wego-muted">Page {{ page + 1 }}</span>
              <WegoButton type="button" variant="secondary" :disabled="!hasNextPage" @click="nextPage">Next</WegoButton>
            </div>
          </template>
        </section>

        <section v-if="canManage()" class="mt-10 rounded-wego-card border border-wego-border bg-wego-surface p-6">
          <h2 class="text-xl font-semibold">{{ editingDiverId ? "Edit diver profile" : "New diver profile" }}</h2>
          <form class="mt-6 space-y-5" @submit.prevent="submitForm">
            <WegoInput id="fullName" v-model="form.fullName" label="Full name" required />
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="nationality" v-model="form.nationality" label="Nationality (optional)" />
              <WegoInput id="primaryLanguage" v-model="form.primaryLanguage" label="Primary language (optional)" />
            </div>
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="email" v-model="form.email" label="Email" type="email" />
              <WegoInput id="phone" v-model="form.phone" label="Phone" />
            </div>
            <p class="text-xs text-wego-muted">An email or a phone number is required.</p>
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="emergencyContactName" v-model="form.emergencyContactName" label="Emergency contact name (optional)" />
              <WegoInput id="emergencyContactPhone" v-model="form.emergencyContactPhone" label="Emergency contact phone (optional)" />
            </div>
            <div>
              <label for="medicalNotes" class="block text-sm font-medium text-wego-muted">Medical notes (optional)</label>
              <textarea
                id="medicalNotes"
                v-model="form.medicalNotes"
                rows="3"
                class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
              />
              <p class="mt-1 text-xs text-wego-muted">Staff reference only — never a dive-fitness decision.</p>
            </div>
            <div class="grid gap-5 sm:grid-cols-3">
              <WegoInput id="totalLoggedDives" v-model="form.totalLoggedDives" label="Total logged dives" type="number" />
              <WegoInput id="maxDepthMeters" v-model="form.maxDepthMeters" label="Max depth (m, optional)" type="number" />
              <WegoInput id="lastDiveOn" v-model="form.lastDiveOn" label="Last dive on (optional)" type="date" />
            </div>
            <div class="grid gap-5 sm:grid-cols-3">
              <WegoInput id="bcdSize" v-model="form.bcdSize" label="BCD size (optional)" />
              <WegoInput id="finSize" v-model="form.finSize" label="Fin size (optional)" />
              <WegoInput id="wetsuitSize" v-model="form.wetsuitSize" label="Wetsuit size (optional)" />
            </div>

            <div>
              <div class="flex items-center justify-between">
                <span class="block text-sm font-medium text-wego-muted">Certifications</span>
                <WegoButton type="button" variant="secondary" @click="addCertificationRow">Add certification</WegoButton>
              </div>
              <div v-for="(certification, index) in form.certifications" :key="index" class="mt-3 grid gap-3 sm:grid-cols-4">
                <WegoInput :id="`cert-agency-${index}`" v-model="certification.agency" label="Agency" />
                <WegoInput :id="`cert-level-${index}`" v-model="certification.level" label="Level" />
                <WegoInput
                  :id="`cert-number-${index}`"
                  :model-value="certification.certificationNumber ?? ''"
                  label="Number (optional)"
                  @update:model-value="(value) => (certification.certificationNumber = value)"
                />
                <div class="flex items-end gap-2">
                  <WegoInput
                    :id="`cert-issued-${index}`"
                    :model-value="certification.issuedOn ?? ''"
                    label="Issued (optional)"
                    type="date"
                    @update:model-value="(value) => (certification.issuedOn = value)"
                  />
                  <WegoButton type="button" variant="secondary" @click="removeCertificationRow(index)">Remove</WegoButton>
                </div>
              </div>
            </div>

            <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

            <div class="flex gap-3">
              <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
                {{ editingDiverId ? "Save changes" : "Create profile" }}
              </WegoButton>
              <WegoButton v-if="editingDiverId" type="button" variant="secondary" @click="cancelEdit">Cancel</WegoButton>
            </div>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
