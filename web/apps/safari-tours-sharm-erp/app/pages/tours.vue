<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import {
  listTours,
  formatMoney,
  ToursApiError,
  type Tour,
  type TourCategory,
  PAGE_SIZE,
} from "../composables/useToursApi";

useHead({ title: "Tours · Safari Tours Sharm" });

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const tours   = ref<Tour[]>([]);
const state   = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error   = ref("");
const page    = ref(0);
const hasNext = ref(false);

const filterCategory = ref<TourCategory | "">("");
const filterActive   = ref<"" | "true" | "false">("");

const canManage = computed(() => hasPermission(session.value, "tours-operator.tour:manage"));
const canView   = computed(() => hasPermission(session.value, "tours-operator.tour:view"));

const CATEGORIES: { value: TourCategory | ""; label: string }[] = [
  { value: "",          label: "All Categories" },
  { value: "DESERT",    label: "Desert" },
  { value: "SEA",       label: "Sea" },
  { value: "CULTURAL",  label: "Cultural" },
  { value: "SHOWS",     label: "Shows" },
  { value: "TRANSFERS", label: "Transfers" },
];

function categoryLabel(cat: TourCategory): string {
  return CATEGORIES.find((c) => c.value === cat)?.label ?? cat;
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  state.value = "loading";
  error.value = "";
  try {
    const params: Parameters<typeof listTours>[1] = {
      page: page.value,
      size: PAGE_SIZE,
    };
    if (filterCategory.value) params.category = filterCategory.value as TourCategory;
    if (filterActive.value !== "") params.activeOnly = filterActive.value === "true";

    const result  = await listTours(session.value.token, params);
    tours.value   = result;
    hasNext.value = result.length === PAGE_SIZE;
    state.value   = "loaded";
  } catch (err) {
    handleApiError(err);
    error.value = err instanceof ToursApiError ? err.errorCode : "Failed to load tours.";
    state.value = "error";
  }
}

function applyFilters() {
  page.value = 0;
  void load();
}

function resetFilters() {
  filterCategory.value = "";
  filterActive.value   = "";
  page.value = 0;
  void load();
}

function logout() {
  clearAuthSession();
  void router.replace("/login");
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  if (!canView.value && !canManage.value) { void router.replace("/"); return; }
  void load();
});
</script>

<template>
  <main class="min-h-screen bg-sts-canvas px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-3">
            <NuxtLink to="/" class="text-sm text-sts-muted hover:text-sts-ocean">← Overview</NuxtLink>
          </div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">Tours</h1>
        </div>
        <WegoButton type="button" variant="secondary" size="sm" class="text-sts-muted" @click="logout">
          Sign out
        </WegoButton>
      </header>

      <!-- Nav links -->
      <nav class="mt-4 flex gap-4 text-sm" aria-label="Section navigation">
        <NuxtLink to="/"         class="text-sts-muted hover:text-sts-ocean">Overview</NuxtLink>
        <NuxtLink to="/bookings" class="text-sts-muted hover:text-sts-ocean">Bookings</NuxtLink>
        <NuxtLink to="/tours"    class="font-semibold text-sts-ocean border-b-2 border-sts-ocean pb-0.5">Tours</NuxtLink>
      </nav>

      <!-- Permission check -->
      <WegoAlert v-if="!canView && !canManage" variant="danger" class="mt-6">
        You don't have permission to view tours.
      </WegoAlert>

      <template v-else>
        <!-- Filters -->
        <div class="mt-6 flex flex-wrap gap-3">
          <select
            v-model="filterCategory"
            aria-label="Filter by category"
            class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          >
            <option v-for="c in CATEGORIES" :key="c.value" :value="c.value">{{ c.label }}</option>
          </select>
          <select
            v-model="filterActive"
            aria-label="Filter by status"
            class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          >
            <option value="">All statuses</option>
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
          <WegoButton type="button" variant="primary" size="sm" @click="applyFilters">
            Filter
          </WegoButton>
          <WegoButton type="button" variant="secondary" size="sm" @click="resetFilters">
            Reset
          </WegoButton>
        </div>

        <!-- Error -->
        <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ error }}</WegoAlert>

        <!-- Loading -->
        <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted">Loading…</p>

        <!-- Empty -->
        <p v-else-if="state === 'loaded' && tours.length === 0" class="mt-6 text-sm text-sts-muted">
          No tours found.
        </p>

        <!-- Table -->
        <div v-else-if="tours.length > 0" class="mt-6 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface shadow-sm">
          <div class="overflow-x-auto">
            <table class="w-full text-sm" aria-label="Tours list">
              <thead>
                <tr class="border-b border-sts-border bg-sts-canvas/60">
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Slug / Reference</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Category</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Duration</th>
                  <th scope="col" class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">Adult price</th>
                  <th scope="col" class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">Child price</th>
                  <th scope="col" class="px-4 py-3 text-center text-xs font-semibold text-sts-muted">Capacity</th>
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Status</th>
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="tour in tours"
                  :key="tour.id"
                  class="border-b border-sts-border/50 hover:bg-sts-canvas/50 last:border-0"
                >
                  <td class="ref px-5 py-3.5 font-mono text-xs text-sts-muted">{{ tour.slug }}</td>
                  <td class="px-4 py-3.5">
                    <span class="badge badge-CONFIRMED">{{ categoryLabel(tour.category) }}</span>
                  </td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ tour.durationText }}</td>
                  <td class="money px-4 py-3.5 text-end font-semibold">{{ formatMoney(tour.priceAdult) }}</td>
                  <td class="money px-4 py-3.5 text-end text-sts-muted">
                    {{ tour.priceChild != null ? formatMoney(tour.priceChild) : '—' }}
                  </td>
                  <td class="px-4 py-3.5 text-center tabular-nums">{{ tour.capacity }}</td>
                  <td class="px-5 py-3.5">
                    <span :class="`badge ${tour.isActive ? 'badge-CONFIRMED' : 'badge-EXPIRED'}`">
                      {{ tour.isActive ? 'Active' : 'Inactive' }}
                    </span>
                  </td>
                  <td class="px-5 py-3.5">
                    <NuxtLink
                      :to="`/tours/${tour.id}/slots`"
                      class="text-xs font-semibold text-sts-ocean hover:underline underline-offset-2"
                    >
                      View slots →
                    </NuxtLink>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Pagination -->
        <div v-if="state === 'loaded'" class="mt-4 flex items-center justify-end gap-3">
          <WegoButton
            type="button" variant="secondary" size="sm"
            :disabled="page === 0"
            @click="page--; load()"
          >Previous</WegoButton>
          <span class="text-sm text-sts-muted">Page {{ page + 1 }}</span>
          <WegoButton
            type="button" variant="secondary" size="sm"
            :disabled="!hasNext"
            @click="page++; load()"
          >Next</WegoButton>
        </div>
      </template>

    </div>
  </main>
</template>
