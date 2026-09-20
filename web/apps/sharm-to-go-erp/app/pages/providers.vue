<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoInput, WegoPageHeader, WegoPagination, WegoPanel } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  archiveProvider,
  createProvider,
  listProviders,
  PAGE_SIZE,
  type Provider,
  type ProviderStatus,
  TravelMarketplaceApiError,
  updateProvider,
  type UpsertProviderBody,
} from "../composables/useTravelMarketplaceApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Providers · Sharm To Go" });

const session = ref<AuthSession | null>(null);
const providers = ref<Provider[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const page = ref(0);
const hasNextPage = ref(false);
const statusFilter = ref<ProviderStatus | "">("ACTIVE");
const search = ref("");

function blankForm() {
  return { name: "", contactEmail: "", contactPhone: "" };
}

const form = ref(blankForm());
const editingProviderId = ref<string | null>(null);
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

const archiveState = ref<Record<string, "idle" | "submitting" | "error">>({});
const archiveError = ref<Record<string, string>>({});

const canManage = () => hasPermission(session.value, "provider:manage");
const canView = () => hasPermission(session.value, "provider:view");

function handleApiError(error: unknown) {
  if (error instanceof TravelMarketplaceApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof TravelMarketplaceApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "already_archived") return "That provider is already archived.";
    if (error.status === 404) return "Not found.";
    if (error.status === 400) return "Check the form — a provider needs a name and an email or a phone number.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadProviders() {
  if (!session.value) return;
  if (!canView()) {
    providers.value = [];
    hasNextPage.value = false;
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const result = await listProviders(session.value.token, {
      status: statusFilter.value || undefined,
      search: search.value || undefined,
      page: page.value,
    });
    providers.value = result;
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
  loadProviders();
}

function previousPage() {
  if (page.value === 0) return;
  page.value -= 1;
  loadProviders();
}

function runSearch() {
  page.value = 0;
  loadProviders();
}

function startEdit(provider: Provider) {
  editingProviderId.value = provider.id;
  form.value = { name: provider.name, contactEmail: provider.contactEmail ?? "", contactPhone: provider.contactPhone ?? "" };
  formState.value = "idle";
  formError.value = "";
}

function cancelEdit() {
  editingProviderId.value = null;
  form.value = blankForm();
  formState.value = "idle";
  formError.value = "";
}

function buildRequestBody(): UpsertProviderBody {
  return {
    name: form.value.name,
    contactEmail: form.value.contactEmail || undefined,
    contactPhone: form.value.contactPhone || undefined,
  };
}

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    const body = buildRequestBody();
    if (editingProviderId.value) {
      const updated = await updateProvider(session.value.token, editingProviderId.value, body);
      providers.value = providers.value.map((existing) => (existing.id === updated.id ? updated : existing));
    } else {
      const created = await createProvider(session.value.token, body);
      providers.value = [created, ...providers.value];
    }
    cancelEdit();
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

async function submitArchive(provider: Provider) {
  if (!session.value) return;
  if (!window.confirm(`Archive "${provider.name}"? It stops appearing in the active list, but nothing is deleted.`)) return;

  archiveState.value[provider.id] = "submitting";
  archiveError.value[provider.id] = "";
  try {
    const updated = await archiveProvider(session.value.token, provider.id);
    if (statusFilter.value === "ACTIVE") {
      providers.value = providers.value.filter((existing) => existing.id !== updated.id);
    } else {
      providers.value = providers.value.map((existing) => (existing.id === updated.id ? updated : existing));
    }
    archiveState.value[provider.id] = "idle";
  } catch (error) {
    handleApiError(error);
    archiveState.value[provider.id] = "error";
    archiveError.value[provider.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadProviders();
});
</script>

<template>
  <WegoPageHeader
    eyebrow="Sharm To Go"
    title="Providers"
    description="The organizations operationally/legally delivering a service when it isn't Sharm To Go itself."
  />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view providers.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to list providers (provider:view).
      </p>
      <template v-else>
        <div class="flex flex-wrap items-end gap-3">
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
        <p v-else-if="listState === 'loaded' && providers.length === 0 && page === 0" class="mt-3 text-sm text-wego-muted">
          No providers yet.
        </p>
        <ul v-else-if="providers.length > 0" class="mt-4 space-y-3">
          <li v-for="provider in providers" :key="provider.id" class="rounded-wego-control border border-wego-border p-4">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="font-semibold">{{ provider.name }}</p>
                <p class="mt-1 text-sm text-wego-muted">
                  <span v-if="provider.contactEmail">{{ provider.contactEmail }}</span>
                  <span v-if="provider.contactEmail && provider.contactPhone"> · </span>
                  <span v-if="provider.contactPhone">{{ provider.contactPhone }}</span>
                </p>
                <WegoBadge :tone="provider.status === 'ACTIVE' ? 'success' : 'neutral'" class="mt-2">{{ provider.status }}</WegoBadge>
              </div>
              <div v-if="canManage()" class="flex shrink-0 gap-2">
                <WegoButton type="button" variant="secondary" @click="startEdit(provider)">Edit</WegoButton>
                <WegoButton
                  v-if="provider.status === 'ACTIVE'"
                  type="button"
                  variant="secondary"
                  :disabled="archiveState[provider.id] === 'submitting'"
                  @click="submitArchive(provider)"
                >
                  Archive
                </WegoButton>
              </div>
            </div>
            <WegoAlert v-if="archiveState[provider.id] === 'error'" variant="danger" class="mt-2">
              {{ archiveError[provider.id] }}
            </WegoAlert>
          </li>
        </ul>

        <WegoPagination class="mt-4" :page="page" :has-next-page="hasNextPage" @previous="previousPage" @next="nextPage" />
      </template>
    </WegoPanel>

    <WegoPanel v-if="canManage()" class="mt-10" :title="editingProviderId ? 'Edit provider' : 'New provider'">
      <form class="space-y-5" @submit.prevent="submitForm">
        <WegoInput id="name" v-model="form.name" label="Name" required />
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="contactEmail" v-model="form.contactEmail" label="Contact email" type="email" />
          <WegoInput id="contactPhone" v-model="form.contactPhone" label="Contact phone" />
        </div>
        <p class="text-xs text-wego-muted">An email or a phone number is required.</p>

        <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

        <div class="flex gap-3">
          <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
            {{ editingProviderId ? "Save changes" : "Create provider" }}
          </WegoButton>
          <WegoButton v-if="editingProviderId" type="button" variant="secondary" @click="cancelEdit">Cancel</WegoButton>
        </div>
      </form>
    </WegoPanel>
  </template>
</template>
