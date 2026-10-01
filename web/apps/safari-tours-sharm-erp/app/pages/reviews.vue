<script setup lang="ts">
import { computed, onMounted, ref } from "vue";

import {
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";

useHead({ title: "Reviews · Safari Tours Sharm" });

const router  = useRouter();
const session = ref<AuthSession | null>(null);

// Reviews endpoint is Phase 5 — display placeholder with coming-soon state
const _canView = computed(() => hasPermission(session.value, "tours-operator.tour:view"));

const _filterSource = ref<"" | "TRIPADVISOR" | "GOOGLE" | "BOOKING" | "INTERNAL">("");
const _filterRating = ref<"" | "5" | "4" | "3" | "2" | "1">("");

// Sources are labels only; no rating or volume is displayed without verified runtime data.
const summary = {
  breakdown: [
    { source: "TripAdvisor", count: 0, avg: 0 },
    { source: "Google",      count: 0, avg: 0 },
    { source: "Booking.com", count: 0, avg: 0 },
    { source: "Direct",      count: 0, avg: 0 },
  ],
};


onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-5xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">Reviews</h1>
        </div>
      </header>

      <!-- Nav -->

      <!-- Coming soon banner -->
      <div class="mt-8 rounded-2xl border border-sts-gold bg-sts-gold-soft px-6 py-5">
        <div class="flex items-start gap-4">
          <div class="mt-0.5 shrink-0 text-2xl">⭐</div>
          <div>
            <p class="font-semibold text-sts-ocean">Reviews module — coming in Phase 5</p>
            <p class="mt-1 text-sm text-sts-muted">
              The reviews API will aggregate ratings from TripAdvisor, Google, Booking.com, and direct
              post-tour feedback collected via WhatsApp. This dashboard will show review volume, average
              rating per tour, and response tracking.
            </p>
          </div>
        </div>
      </div>

      <!-- Score summary (placeholder) -->
      <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <article class="rounded-2xl border border-sts-border bg-sts-surface p-5 shadow-sm">
          <p class="text-sm font-medium text-sts-muted">Overall rating</p>
          <p class="mt-3 text-4xl font-bold tracking-tight text-sts-ocean">—</p>
          <p class="mt-1 text-xs text-sts-muted">Pending API integration</p>
        </article>
        <article
          v-for="ch in summary.breakdown"
          :key="ch.source"
          class="rounded-2xl border border-sts-border bg-sts-surface p-5 shadow-sm"
        >
          <p class="text-sm font-medium text-sts-muted">{{ ch.source }}</p>
          <p class="mt-3 text-4xl font-bold tracking-tight text-sts-muted">—</p>
          <p class="mt-1 text-xs text-sts-muted">No data yet</p>
        </article>
      </div>

      <!-- Filters (disabled until Phase 5) -->
      <div class="mt-6 flex flex-wrap gap-3 opacity-50 pointer-events-none" aria-hidden="true">
        <select class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm">
          <option>All sources</option>
          <option>TripAdvisor</option>
          <option>Google</option>
          <option>Booking.com</option>
          <option>Direct</option>
        </select>
        <select class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm">
          <option>All ratings</option>
          <option>5 ⭐</option>
          <option>4 ⭐</option>
          <option>3 ⭐</option>
          <option>2 ⭐</option>
          <option>1 ⭐</option>
        </select>
      </div>

      <!-- Empty state -->
      <div class="mt-6 rounded-2xl border border-sts-border bg-sts-surface px-6 py-12 text-center shadow-sm">
        <p class="text-3xl mb-3">📝</p>
        <p class="font-semibold text-sts-ink">No reviews yet</p>
        <p class="mt-1 text-sm text-sts-muted">
          Reviews will appear here once the Phase 5 integration is complete.
        </p>
      </div>

    </div>
  </main>
</template>
