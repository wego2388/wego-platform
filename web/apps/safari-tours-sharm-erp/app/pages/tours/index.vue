<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../../composables/useAuthSession";
import {
  listStaffTours,
  activateTour,
  deactivateTour,
  ToursApiError,
  type Tour,
  type TourCategory,
  PAGE_SIZE,
} from "../../composables/useToursApi";
import { useErpLocale } from "../../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../../utils/bookingMessages";
import { tourErrorMessage } from "../../utils/tourMessages";
import { contentMessage } from "../../utils/contentMessages";
import { categoryMessage } from "../../utils/categoryMessages";

const { locale, t, count, money } = useErpLocale();
useHead(() => ({ title: `${t("nav.tours")} · Safari Tours Sharm` }));

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const tours   = ref<Tour[]>([]);
const state   = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error   = ref<ErpMessageDescriptor | null>(null);
const page    = ref(0);
const hasNext = ref(false);
let loadVersion = 0;

const filterCategory = ref<TourCategory | "">("");
const filterActive   = ref<"" | "true" | "false">("");

/** Per-tour action state: "idle" | "pending" | "error" */
const actionState = ref<Record<string, "idle" | "pending" | "error">>({});
const actionErrors = ref<Record<string, ErpMessageDescriptor>>({});

const canManage = computed(() => hasPermission(session.value, "tours-operator.tour:manage"));
const canView   = computed(() => hasPermission(session.value, "tours-operator.tour:view") || canManage.value);

const CATEGORIES: TourCategory[] = ["DESERT", "SEA", "CULTURAL", "SHOWS", "TRANSFERS"];

function categoryLabel(cat: TourCategory): string {
  return t(`category.${cat}`);
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  const version = ++loadVersion;
  state.value = "loading";
  error.value = null;
  try {
    const params: Parameters<typeof listStaffTours>[1] = {
      page: page.value,
      size: PAGE_SIZE,
    };
    if (filterCategory.value) params.category = filterCategory.value as TourCategory;
    if (filterActive.value !== "") params.activeOnly = filterActive.value === "true";

    const result  = await listStaffTours(session.value.token, params);
    if (version !== loadVersion) return;
    tours.value   = result;
    hasNext.value = result.length === PAGE_SIZE;
    state.value   = "loaded";
    actionState.value = {};
    actionErrors.value = {};
  } catch (err) {
    handleApiError(err);
    if (version !== loadVersion) return;
    error.value = tourErrorMessage(err);
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
    actionErrors.value[tour.id] = tourErrorMessage(err);
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
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ t("nav.tours") }}</h1>
        </div>
        <NuxtLink v-if="canView" to="/categories" class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm font-semibold text-sts-ocean hover:underline">{{ categoryMessage(locale, "title") }}</NuxtLink>
      </header>

      <!-- Nav links -->

      <!-- Permission check -->
      <WegoAlert v-if="!canView" variant="danger" class="mt-6">
        {{ t("tours.noPermission") }}
      </WegoAlert>

      <template v-else>
        <!-- Filters -->
        <div class="mt-6 flex flex-wrap gap-3">
          <select
            v-model="filterCategory"
            :aria-label="t('tours.filterCategory')"
            class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          >
            <option value="">{{ t("tours.allCategories") }}</option>
            <option v-for="category in CATEGORIES" :key="category" :value="category">{{ categoryLabel(category) }}</option>
          </select>
          <select
            v-model="filterActive"
            :aria-label="t('tours.filterStatus')"
            class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          >
            <option value="">{{ t("common.allStatuses") }}</option>
            <option value="true">{{ t("common.active") }}</option>
            <option value="false">{{ t("common.inactive") }}</option>
          </select>
          <WegoButton type="button" variant="primary" size="sm" @click="applyFilters">
            {{ t("common.filter") }}
          </WegoButton>
          <WegoButton type="button" variant="secondary" size="sm" @click="resetFilters">
            {{ t("common.reset") }}
          </WegoButton>
        </div>

        <!-- Error -->
        <WegoAlert v-if="state === 'error' && error" variant="danger" class="mt-6">{{ t(error.key, error.params) }}</WegoAlert>

        <!-- Loading -->
        <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted" role="status">{{ t("common.loading") }}</p>

        <!-- Empty -->
        <p v-else-if="state === 'loaded' && tours.length === 0" class="mt-6 text-sm text-sts-muted">
          {{ t("tours.empty") }}
        </p>

        <!-- Table -->
        <div v-else-if="tours.length > 0" class="mt-6 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface shadow-sm">
          <div class="overflow-x-auto" role="region" :aria-label="t('nav.tours')" tabindex="0">
            <table class="w-full text-sm" :aria-label="t('nav.tours')">
              <thead>
                <tr class="border-b border-sts-border bg-sts-canvas/60">
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("tours.nameSlug") }}</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("tours.category") }}</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("tours.duration") }}</th>
                  <th scope="col" class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">{{ t("tours.price") }}</th>
                  <th scope="col" class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">{{ t("tours.childPrice") }}</th>
                  <th scope="col" class="px-4 py-3 text-center text-xs font-semibold text-sts-muted">{{ t("tours.places") }}</th>
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("common.status") }}</th>
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("common.actions") }}</th>
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
                        {{ money(option.price) }} <span class="text-xs font-normal text-sts-muted">/ {{ option.label }} ({{ count(option.seatsPerUnit) }})</span>
                      </div>
                    </template>
                    <template v-else>{{ money(tour.priceAdult) }} <span class="text-xs font-normal text-sts-muted">/ {{ t("tours.adult") }}</span></template>
                  </td>
                  <td class="money px-4 py-3.5 text-end text-sts-muted">
                    {{ tour.priceBasis === 'PER_UNIT' ? t('tours.perUnit') : tour.priceChild != null ? money(tour.priceChild) : '—' }}
                  </td>
                  <td class="px-4 py-3.5 text-center tabular-nums">{{ count(tour.capacity) }}</td>
                  <td class="px-5 py-3.5">
                    <span :class="`badge ${tour.isActive ? 'badge-CONFIRMED' : 'badge-EXPIRED'}`">
                      {{ tour.isActive ? t('common.active') : t('common.inactive') }}
                    </span>
                  </td>
                  <td class="px-5 py-3.5">
                    <div class="flex flex-wrap items-center gap-2">
                      <NuxtLink :to="`/tours/${tour.id}/content`" class="text-xs font-semibold text-sts-ocean hover:underline underline-offset-2">{{ contentMessage(locale, "title") }}</NuxtLink>
                      <NuxtLink
                        :to="`/tours/${tour.id}/slots`"
                        class="text-xs font-semibold text-sts-ocean hover:underline underline-offset-2"
                      >
                        {{ t("slots.heading") }}
                      </NuxtLink>
                      <template v-if="canManage">
                        <button
                          v-if="!tour.isActive && tour.tourType !== 'REQUEST_ONLY'"
                          type="button"
                          :disabled="actionState[tour.id] === 'pending'"
                          class="text-xs font-semibold text-emerald-700 hover:underline underline-offset-2 disabled:opacity-50"
                          @click="toggleActive(tour)"
                        >
                          {{ actionState[tour.id] === 'pending' ? '…' : t('tours.activate') }}
                        </button>
                        <button
                          v-else-if="tour.isActive"
                          type="button"
                          :disabled="actionState[tour.id] === 'pending'"
                          class="text-xs font-semibold text-rose-600 hover:underline underline-offset-2 disabled:opacity-50"
                          @click="toggleActive(tour)"
                        >
                          {{ actionState[tour.id] === 'pending' ? '…' : t('tours.deactivate') }}
                        </button>
                        <span
                          v-if="actionState[tour.id] === 'error' && actionErrors[tour.id]"
                          class="text-xs text-sts-danger"
                          role="alert"
                        >{{ t(actionErrors[tour.id]!.key, actionErrors[tour.id]!.params) }}</span>
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
          >{{ t("common.previous") }}</WegoButton>
          <span class="text-sm text-sts-muted">{{ t("common.page", { page: count(page + 1) }) }}</span>
          <WegoButton
            type="button" variant="secondary" size="sm"
            :disabled="!hasNext"
            @click="page++; load()"
          >{{ t("common.next") }}</WegoButton>
        </div>
      </template>

    </div>
  </main>
</template>
