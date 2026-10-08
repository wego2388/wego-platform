<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../../../composables/useAuthSession";
import {
  getStaffTour,
  listSlotsByDate,
  blockSlot,
  unblockSlot,
  ToursApiError,
  type Tour,
  type TourSlot,
  type TimeSlot,
} from "../../../composables/useToursApi";
import { useErpLocale } from "../../../composables/useErpLocale";
import { addCalendarDays, calendarWeek, mondayForDay } from "../../../utils/slotCalendar";
import { isCalendarDate, operatorCalendarDay } from "../../../utils/calendarDate";
import CalendarDateField from "../../../components/CalendarDateField.vue";
import DepartureCreator from "../../../components/DepartureCreator.vue";
import type { ErpMessageDescriptor } from "../../../utils/bookingMessages";
import { tourErrorMessage } from "../../../utils/tourMessages";

const { t, count, dateLabel } = useErpLocale();
useHead(() => ({ title: `${t("slots.title")} · Safari Tours Sharm` }));

const route  = useRoute();
const router = useRouter();

const session   = ref<AuthSession | null>(null);
const tour      = ref<Tour | null>(null);
const slots     = ref<TourSlot[]>([]);
const loadState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const loadError = ref<ErpMessageDescriptor | null>(null);
let loadVersion = 0;

// Calendar: navigate by week (Mon–Sun)
// Initialize in the browser to preserve its existing local-day behavior without SSR drift.
const today = ref("");
const weekStart = ref("");

const canManage = computed(() => hasPermission(session.value, "tours-operator.slot:manage"));
const canCreateOffice = computed(() => hasPermission(session.value, "tours-operator.booking:create-office"));
const canView = computed(() => hasPermission(session.value, "tours-operator.tour:view") || hasPermission(session.value, "tours-operator.tour:manage"));
const jumpDate = ref("");
const actionBusy = ref(false);
const actionError = ref<ErpMessageDescriptor | null>(null);
const actionSaved = ref(false);

const tourId = computed(() => String(route.params.id));

// 7 days from weekStart
const weekDays = computed(() => weekStart.value ? calendarWeek(weekStart.value) : []);

const weekLabel = computed(() => {
  const from = weekDays.value[0];
  const to   = weekDays.value[6];
  return from && to ? t("slots.weekRange", { from: dateLabel(from), to: dateLabel(to) }) : "";
});

// Slots indexed by date+timeSlot for O(1) lookup
const slotIndex = computed(() => {
  const idx: Record<string, TourSlot> = {};
  for (const s of slots.value) {
    idx[`${s.date}__${s.timeSlot}`] = s;
  }
  return idx;
});

const TIME_SLOTS: TimeSlot[] = ["SUNRISE", "MORNING", "AFTERNOON", "SUNSET"];

function prevWeek() {
  weekStart.value = addCalendarDays(weekStart.value, -7);
}

function nextWeek() {
  weekStart.value = addCalendarDays(weekStart.value, 7);
}

function slotFor(date: string, time: TimeSlot): TourSlot | null {
  return slotIndex.value[`${date}__${time}`] ?? null;
}

function availabilityClass(slot: TourSlot | null): string {
  if (!slot) return "bg-sts-canvas text-sts-muted";
  if (slot.isBlocked) return "bg-sts-danger-soft text-sts-danger";
  if (slot.available === 0) return "bg-sts-warning-soft text-sts-warning";
  if (slot.available <= 3) return "bg-yellow-50 text-yellow-700";
  return "bg-sts-success-soft text-sts-success";
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value || !canView.value || !weekStart.value) return;
  const version = ++loadVersion;
  loadState.value = "loading";
  loadError.value = null;
  try {
    const [tourResult, slotsResult] = await Promise.all([
      getStaffTour(session.value.token, tourId.value),
      // The existing by-date API returns full + blocked inventory, unlike public availability.
      Promise.all(weekDays.value.map((day) => listSlotsByDate(session.value!.token, tourId.value, day))).then((days) => days.flat()),
    ]);
    if (version !== loadVersion) return;
    tour.value      = tourResult;
    slots.value     = slotsResult;
    loadState.value = "loaded";
  } catch (e) {
    handleApiError(e);
    if (version !== loadVersion) return;
    loadError.value = tourErrorMessage(e);
    loadState.value = "error";
  }
}

function sessionExpired() { clearAuthSession(); void router.replace("/login"); }
async function departureSaved(slot: TourSlot) {
  jumpDate.value = slot.date;
  const monday = mondayForDay(slot.date);
  if (weekStart.value === monday) await load(); else weekStart.value = monday;
}
async function toggleSlot(slot: TourSlot) {
  if (!session.value || !canManage.value || actionBusy.value) return;
  if (!window.confirm(t(slot.isBlocked ? "inventory.unblockConfirm" : "inventory.blockConfirm", { date: dateLabel(slot.date), time: t(`slot.${slot.timeSlot}`) }))) return;
  actionBusy.value = true; actionError.value = null; actionSaved.value = false;
  try {
    if (slot.isBlocked) await unblockSlot(session.value.token, tourId.value, slot.id);
    else await blockSlot(session.value.token, tourId.value, slot.id);
    await load(); actionSaved.value = true;
  } catch (e) { handleApiError(e); actionError.value = tourErrorMessage(e); }
  finally { actionBusy.value = false; }
}
watch(jumpDate, (day) => { if (isCalendarDate(day)) weekStart.value = mondayForDay(day); });

watch(weekStart, () => { void load(); });

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  if (!canView.value) { void router.replace("/"); return; }
  today.value = operatorCalendarDay();
  jumpDate.value = today.value;
  weekStart.value = mondayForDay(today.value); // watcher issues exactly one initial load
});
</script>

<template>
  <main class="px-4 py-8 text-sts-ink sm:px-8 lg:px-14">
    <div class="mx-auto max-w-7xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <NuxtLink to="/tours" class="text-sm text-sts-muted hover:text-sts-ocean">{{ t("nav.tours") }}</NuxtLink>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">
            {{ t("slots.heading") }}
            <span v-if="tour" class="font-mono text-sts-ocean text-lg break-all">{{ tour.slug }}</span>
            <span v-if="tour && !tour.isActive" class="badge badge-EXPIRED">{{ t("common.inactive") }}</span>
          </h1>
        </div>
      </header>

      <!-- Nav -->

      <!-- Error -->
      <WegoAlert v-if="loadState === 'error' && loadError" variant="danger" class="mt-6">{{ t(loadError.key, loadError.params) }}</WegoAlert>
      <WegoAlert v-if="actionError" variant="danger" class="mt-4" role="alert">{{ t(actionError.key, actionError.params) }}</WegoAlert>
      <WegoAlert v-if="actionSaved" variant="success" class="mt-4" role="status">{{ t('inventory.slotChanged') }}</WegoAlert>
      <DepartureCreator v-if="tour && session && canManage" class="mt-6" :token="session.token" :tour="tour" :disabled="actionBusy" @saved="departureSaved" @conflict="load" @expired="sessionExpired" />

      <!-- Week nav -->
      <div v-if="weekStart" class="mt-6 flex flex-wrap items-center gap-4">
        <CalendarDateField id="calendar-jump" v-model="jumpDate" :label="t('common.date')" class="w-full sm:w-80" />
        <WegoButton type="button" variant="secondary" size="sm" @click="prevWeek">{{ t("slots.previousWeek") }}</WegoButton>
        <span class="text-sm font-semibold text-sts-ocean tabular-nums">{{ weekLabel }}</span>
        <WegoButton type="button" variant="secondary" size="sm" @click="nextWeek">{{ t("slots.nextWeek") }}</WegoButton>
      </div>

      <!-- Legend -->
      <p class="mt-4 text-sm text-sts-muted">{{ t("inventory.fullScope") }}</p>
      <div class="mt-3 flex flex-wrap gap-3 text-xs">
        <span class="flex items-center gap-1.5"><span class="inline-block h-3 w-3 rounded bg-sts-success-soft"/>{{ t("slots.available") }}</span>
        <span class="flex items-center gap-1.5"><span class="inline-block h-3 w-3 rounded bg-yellow-50 border border-yellow-200"/>{{ t("slots.low") }}</span>
        <span class="flex items-center gap-1.5"><span class="inline-block h-3 w-3 rounded bg-sts-canvas border border-sts-border"/>{{ t("slots.none") }}</span>
      </div>

      <!-- Loading -->
      <p v-if="loadState === 'loading' || loadState === 'idle'" class="mt-6 text-sm text-sts-muted" role="status">{{ t("common.loading") }}</p>
      <p v-if="loadState === 'loaded' && slots.length === 0" class="mt-6 text-sm text-sts-muted">{{ t("inventory.empty") }}</p>

      <!-- Calendar grid -->
      <div v-if="loadState === 'loaded'" class="mt-4 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface shadow-sm" role="region" :aria-label="t('slots.calendar')" tabindex="0">
        <table class="w-full min-w-[960px] text-sm border-collapse" :aria-label="t('slots.calendar')">
          <thead>
            <tr class="border-b border-sts-border bg-sts-canvas/60">
              <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted w-24">{{ t("slots.time") }}</th>
              <th
                v-for="day in weekDays"
                :key="day"
                scope="col"
                class="px-3 py-3 text-center text-xs font-semibold text-sts-muted"
                :class="day === today ? 'bg-sts-gold-soft' : ''"
              >
                {{ dateLabel(day, true) }}
                <template v-if="day === today"><span class="ms-1 text-sts-gold font-bold" aria-hidden="true">●</span><span class="sr-only">{{ t("slots.today") }}</span></template>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="timeSlot in TIME_SLOTS"
              :key="timeSlot"
              class="border-b border-sts-border/40 last:border-0"
            >
              <th scope="row" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted whitespace-nowrap">{{ t(`slot.${timeSlot}`) }}</th>
              <td
                v-for="day in weekDays"
                :key="day"
                class="px-2 py-2 text-center"
                :class="day === today ? 'bg-sts-gold-soft/40' : ''"
              >
                <template v-if="slotFor(day, timeSlot)">
                  <div
                    class="rounded-lg px-2 py-1.5 text-xs font-semibold"
                    :class="availabilityClass(slotFor(day, timeSlot))"
                    :title="slotFor(day, timeSlot)?.isBlocked ? t('slots.blocked') : t('slots.placesFree', { available: count(slotFor(day, timeSlot)!.available), capacity: count(slotFor(day, timeSlot)!.capacity) })"
                  >
                    <template v-if="slotFor(day, timeSlot)?.isBlocked">{{ t("slots.blocked") }}</template>
                    <template v-else>
                      <NuxtLink v-if="canCreateOffice && slotFor(day, timeSlot)!.available > 0 && day >= today" :to="{ path: '/bookings/new', query: { tourId, date: day, slotId: slotFor(day, timeSlot)!.id } }" class="block underline" :aria-label="`${t('office.new.cta')} — ${dateLabel(day)} ${t(`slot.${timeSlot}`)}`">
                        <span class="block tabular-nums">{{ count(slotFor(day, timeSlot)!.available) }}/{{ count(slotFor(day, timeSlot)!.capacity) }}</span>
                        <span class="text-[10px]">{{ t("office.new.cta") }}</span>
                      </NuxtLink>
                      <span v-else class="block tabular-nums">{{ count(slotFor(day, timeSlot)!.available) }}/{{ count(slotFor(day, timeSlot)!.capacity) }}</span>
                      <span v-if="!(canCreateOffice && slotFor(day, timeSlot)!.available > 0 && day >= today)" class="text-[10px]">{{ t("slots.available") }}</span>
                    </template>
                  </div>
                  <button v-if="canManage && day >= today" type="button" class="mt-1 text-xs font-semibold text-sts-ocean underline" :disabled="actionBusy" @click="toggleSlot(slotFor(day, timeSlot)!)">{{ t(slotFor(day, timeSlot)!.isBlocked ? 'inventory.unblock' : 'inventory.block') }}</button>
                </template>
                <template v-else>
                  <span class="text-xs text-sts-muted">—</span>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

    </div>
  </main>
</template>
