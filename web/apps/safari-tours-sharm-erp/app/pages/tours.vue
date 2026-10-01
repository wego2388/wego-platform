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
  listStaffTours,
  activateTour,
  deactivateTour,
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

/** Per-tour action state: "idle" | "pending" | "error" */
const actionState = ref<Record<string, "idle" | "pending" | "error">>({});

const canManage = computed(() => hasPermission(session.value, "tours-operator.tour:manage"));
const canView   = computed(() => hasPermission(session.value, "tours-operator.tour:view") || canManage.value);

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
    const params: Parameters<typeof listStaffTours>[1] = {
      page: page.value,
      size: PAGE_SIZE,
    };
    if (filterCategory.value) params.category = filterCategory.value as TourCategory;
    if (filterActive.value !== "") params.activeOnly = filterActive.value === "true";

    const result  = await listStaffTours(session.value.token, params);
    tours.value   = result;
    hasNext.value = result.length === PAGE_SIZE;
    state.value   = "loaded";
    actionState.value = {};
  } catch (err) {
    handleApiError(err);
    error.value = err instanceof ToursApiError ? err.errorCode : "Failed to load tours.";
    state.value = "error";
  }
}

async function toggleActive(tour: Tour) {
  if (!session.value) return;
  actionState.value[tour.id] = "pending";
  try {
    if (tour.isActive) {
      await deactivateTour(session.value.token, tour.id);
    } else {
      await activateTour(session.value.token, tour.id);
    }
    await load();
  } catch (err) {
    handleApiError(err);
    actionState.value[tour.id] = "error";
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


onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  if (!canView.value && !canManage.value) { void router.replace("/"); return; }
  void load();
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-3"/>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">Tours</h1>
        </div>
      </header>

      <!-- Nav links -->

      <!-- Permission check -->
      <WegoAlert v-if="!canView" variant="danger" class="mt-6">
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
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Name / Slug</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Category</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Duration</th>
                  <th scope="col" class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">Price</th>
                  <th scope="col" class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">Child price</th>
                  <th scope="col" class="px-4 py-3 text-center text-xs font-semibold text-sts-muted">Places / departure</th>
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
                  <td class="px-5 py-3.5">
                    <div class="font-medium text-sm">{{ tour.nameEn ?? tour.slug }}</div>
                    <div class="font-mono text-xs text-sts-muted">{{ tour.slug }}</div>
                  </td>
                  <td class="px-4 py-3.5">
                    <span class="badge badge-CONFIRMED">{{ categoryLabel(tour.category) }}</span>
                  </td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ tour.durationText }}</td>
                  <td class="money px-4 py-3.5 text-end font-semibold">
                    <template v-if="tour.priceBasis === 'PER_UNIT'">
                      <div v-for="option in tour.priceOptions" :key="option.code" class="whitespace-nowrap">
                        {{ formatMoney(option.price) }} <span class="text-xs font-normal text-sts-muted">/ {{ option.label }} ({{ option.seatsPerUnit }})</span>
                      </div>
                    </template>
                    <template v-else>{{ formatMoney(tour.priceAdult) }} <span class="text-xs font-normal text-sts-muted">/ adult</span></template>
                  </td>
                  <td class="money px-4 py-3.5 text-end text-sts-muted">
                    {{ tour.priceBasis === 'PER_UNIT' ? 'per unit' : tour.priceChild != null ? formatMoney(tour.priceChild) : '—' }}
                  </td>
                  <td class="px-4 py-3.5 text-center tabular-nums">{{ tour.capacity }}</td>
                  <td class="px-5 py-3.5">
                    <span :class="`badge ${tour.isActive ? 'badge-CONFIRMED' : 'badge-EXPIRED'}`">
                      {{ tour.isActive ? 'Active' : 'Inactive' }}
                    </span>
                  </td>
                  <td class="px-5 py-3.5">
                    <div class="flex flex-wrap items-center gap-2">
                      <NuxtLink
                        :to="`/tours/${tour.id}/slots`"
                        class="text-xs font-semibold text-sts-ocean hover:underline underline-offset-2"
                      >
                        Slots →
                      </NuxtLink>
                      <template v-if="canManage">
                        <button
                          v-if="!tour.isActive && tour.tourType !== 'REQUEST_ONLY'"
                          type="button"
                          :disabled="actionState[tour.id] === 'pending'"
                          class="text-xs font-semibold text-emerald-600 hover:underline underline-offset-2 disabled:opacity-50"
                          @click="toggleActive(tour)"
                        >
                          {{ actionState[tour.id] === 'pending' ? '…' : 'Activate' }}
                        </button>
                        <button
                          v-else-if="tour.isActive"
                          type="button"
                          :disabled="actionState[tour.id] === 'pending'"
                          class="text-xs font-semibold text-rose-600 hover:underline underline-offset-2 disabled:opacity-50"
                          @click="toggleActive(tour)"
                        >
                          {{ actionState[tour.id] === 'pending' ? '…' : 'Deactivate' }}
                        </button>
                        <span
                          v-if="actionState[tour.id] === 'error'"
                          class="text-xs text-rose-500"
                          role="alert"
                        >Failed</span>
                      </template>
                    </div>
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
