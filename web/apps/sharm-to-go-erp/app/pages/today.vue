<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoPageHeader, WegoPanel } from "@wego/ui";
import { type AuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import { listTravelRequests, type TravelRequest, TravelMarketplaceApiError } from "../composables/useTravelMarketplaceApi";

definePageMeta({ layout: "app-shell" });

useHead({
  title: "Today · Sharm To Go",
  meta: [{ name: "description", content: "Today's confirmed pickups and departures, in order." }],
});

const session = ref<AuthSession | null>(null);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
// Only CONFIRMED requests are real work for today — NEW/IN_REVIEW still need
// a staff decision first (that's the Requests queue's job), and
// COMPLETED/CANCELLED/EXPIRED are no longer actionable.
const confirmedRequests = ref<TravelRequest[]>([]);
const newCount = ref(0);

const canView = () => hasPermission(session.value, "travel-request:view");

function errorText(error: unknown): string {
  if (error instanceof TravelMarketplaceApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission to view today's run sheet.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

const todayIso = new Date().toISOString().slice(0, 10);

// Pulled from up to 200 recently-confirmed requests, same accepted
// limitation as the dashboard's own counts (no dedicated
// "confirmed for date X" endpoint exists yet) — honest about the ceiling
// rather than silently truncating.
async function loadToday() {
  if (!session.value || !canView()) {
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const [confirmed, fresh] = await Promise.all([
      listTravelRequests(session.value.token, { status: "CONFIRMED", size: 200 }),
      listTravelRequests(session.value.token, { status: "NEW", size: 200 }),
    ]);
    confirmedRequests.value = confirmed;
    newCount.value = fresh.length;
    listState.value = "loaded";
  } catch (error) {
    listState.value = "error";
    listError.value = errorText(error);
  }
}

const todaysRuns = computed(() =>
  confirmedRequests.value
    .filter((request) => request.requestedDate === todayIso)
    .sort((a, b) => (a.requestedTime ?? "99:99").localeCompare(b.requestedTime ?? "99:99")),
);

function formatTime(value: string | undefined): string {
  return value ?? "Time to be confirmed";
}

onMounted(() => {
  session.value = readAuthSession();
  loadToday();
});
</script>

<template>
  <div class="contents">
  <WegoPageHeader eyebrow="Sharm To Go" title="Today" description="Confirmed pickups and departures for today, in time order." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view today's run sheet.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoAlert v-if="listState === 'loaded' && newCount > 0" variant="warning" class="mt-6">
      {{ newCount }} new request(s) still need a staff decision —
      <NuxtLink to="/requests" class="font-semibold underline">go to the Requests queue</NuxtLink>.
    </WegoAlert>

    <WegoPanel class="mt-6">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to view today's run sheet (travel-request:view).
      </p>

      <template v-else>
        <p v-if="listState === 'loading'" class="text-sm text-wego-muted">Loading…</p>

        <p v-else-if="listState === 'loaded' && todaysRuns.length === 0" class="text-sm text-wego-muted">
          Nothing confirmed for today yet.
        </p>

        <ol v-else class="space-y-3">
          <li v-for="request in todaysRuns" :key="request.id" class="rounded-wego-control border border-wego-border p-4">
            <NuxtLink :to="`/requests/${request.id}`" class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="font-mono text-sm font-semibold text-wego-accent">{{ formatTime(request.requestedTime) }}</p>
                <p class="mt-1 font-semibold">{{ request.serviceName.en }} — {{ request.optionLabel.en }}</p>
                <p class="mt-1 text-sm text-wego-muted">
                  {{ request.customer.name }}<span v-if="request.customer.phone"> · {{ request.customer.phone }}</span>
                  · {{ request.adults + request.children }} guest(s)
                  <span v-if="request.hotelOrPickup"> · {{ request.hotelOrPickup }}</span>
                </p>
              </div>
              <WegoBadge tone="success">{{ request.reference }}</WegoBadge>
            </NuxtLink>
          </li>
        </ol>

        <p v-if="confirmedRequests.length >= 200" class="mt-4 text-xs text-wego-warning">
          Showing the 200 most recently confirmed requests only — there may be more. Not an exhaustive total.
        </p>
      </template>
    </WegoPanel>
  </template>
  </div>
</template>
