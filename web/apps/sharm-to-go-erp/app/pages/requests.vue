<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoInput, WegoPageHeader, WegoPagination, WegoPanel } from "@wego/ui";
import { type AuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import { listTravelRequests, type TravelRequest, type TravelRequestStatus, TravelMarketplaceApiError } from "../composables/useTravelMarketplaceApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Requests · Sharm To Go" });

function statusTone(status: TravelRequestStatus): "neutral" | "accent" | "success" | "warning" | "danger" {
  if (status === "NEW") return "warning";
  if (status === "IN_REVIEW") return "accent";
  if (status === "CONFIRMED") return "success";
  if (status === "COMPLETED") return "neutral";
  if (status === "CANCELLED" || status === "EXPIRED") return "danger";
  return "neutral";
}

const session = ref<AuthSession | null>(null);
const requests = ref<TravelRequest[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const page = ref(0);
const hasNextPage = ref(false);
const statusFilter = ref<TravelRequestStatus | "">("");
const searchTerm = ref("");

const canView = () => hasPermission(session.value, "travel-request:view");

function errorText(error: unknown): string {
  if (error instanceof TravelMarketplaceApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission to view the requests queue.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadRequests() {
  if (!session.value) return;
  if (!canView()) {
    requests.value = [];
    hasNextPage.value = false;
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const result = await listTravelRequests(session.value.token, { status: statusFilter.value || undefined, page: page.value });
    requests.value = result;
    hasNextPage.value = result.length === 50;
    listState.value = "loaded";
  } catch (error) {
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function nextPage() {
  page.value += 1;
  loadRequests();
}

function previousPage() {
  if (page.value === 0) return;
  page.value -= 1;
  loadRequests();
}

function runFilter() {
  page.value = 0;
  loadRequests();
}

// Client-side, on the currently loaded page only — the backend roster has no
// free-text search endpoint yet. Matches on the one thing a customer would
// actually read back over the phone (the reference) or a staff member would
// type from memory (the customer's name).
const visibleRequests = computed(() => {
  const term = searchTerm.value.trim().toLowerCase();
  if (!term) return requests.value;
  return requests.value.filter(
    (request) => request.reference.toLowerCase().includes(term) || request.customer.name.toLowerCase().includes(term),
  );
});

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadRequests();
});
</script>

<template>
  <WegoPageHeader eyebrow="Sharm To Go" title="Requests" description="Every customer request, from receipt through confirmation or cancellation." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view requests.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to view the requests queue (travel-request:view).
      </p>
      <template v-else>
        <div class="flex flex-wrap items-end gap-3">
          <div>
            <label for="statusFilter" class="block text-sm font-medium text-wego-muted">Status</label>
            <select
              id="statusFilter"
              v-model="statusFilter"
              class="mt-2 rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
              @change="runFilter"
            >
              <option value="">All</option>
              <option value="NEW">New</option>
              <option value="IN_REVIEW">In review</option>
              <option value="CONFIRMED">Confirmed</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
              <option value="EXPIRED">Expired</option>
            </select>
          </div>
          <WegoInput id="search" v-model="searchTerm" label="Search (reference or customer name)" class="min-w-64" />
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && requests.length === 0 && page === 0" class="mt-3 text-sm text-wego-muted">
          No requests yet.
        </p>
        <p v-else-if="listState === 'loaded' && visibleRequests.length === 0" class="mt-3 text-sm text-wego-muted">
          No requests on this page match "{{ searchTerm }}".
        </p>

        <ul v-else-if="visibleRequests.length > 0" class="mt-4 space-y-3">
          <li
            v-for="request in visibleRequests"
            :key="request.id"
            class="rounded-wego-control border p-4"
            :class="request.status === 'NEW' ? 'border-wego-warning' : 'border-wego-border'"
          >
            <NuxtLink :to="`/requests/${request.id}`" class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="reference font-mono text-sm font-semibold text-wego-accent">{{ request.reference }}</p>
                <p class="mt-1 font-semibold">
                  {{ request.serviceName.en }}
                  <span v-if="request.status === 'NEW'" class="ms-1 text-xs font-bold text-wego-warning">● unclaimed</span>
                </p>
                <p class="mt-1 text-sm text-wego-muted">
                  {{ request.customer.name }} · {{ formatDate(request.requestedDate) }} · {{ request.adults + request.children }}
                  guest(s) · {{ request.sourceChannel }}
                </p>
              </div>
              <WegoBadge :tone="statusTone(request.status)">{{ request.status }}</WegoBadge>
            </NuxtLink>
          </li>
        </ul>

        <WegoPagination class="mt-4" :page="page" :has-next-page="hasNextPage" @previous="previousPage" @next="nextPage" />
      </template>
    </WegoPanel>
  </template>
</template>
