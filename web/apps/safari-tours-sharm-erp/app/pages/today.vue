<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import type { AssignmentOptions, AssignmentView, Booking, Tour, TourSlot } from "@wego/api-contract";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { ToursApiError, getAssignmentOptions, listAllStaffTours, listAssignments, listBookings, listSlotsByDate } from "../composables/useToursApi";
import { whatsappLink } from "../composables/useWhatsApp";
import AssignmentPanel from "../components/AssignmentPanel.vue";
import OfficePaymentBadge from "../components/OfficePaymentBadge.vue";
import { buildRunSheet, isUnpaid } from "../utils/runSheet";
import { useErpLocale } from "../composables/useErpLocale";
import { docMessage } from "../utils/documentMessages";
import { documentPath } from "../utils/documentFormat";
import type { ErpMessageKey } from "../utils/erpLocale";

const { t, locale, count, money, dateLabel: formatDate } = useErpLocale();
useHead(() => ({ title: `${t("nav.today")} · Safari Tours Sharm` }));

const route = useRoute();
const router = useRouter();
const session = ref<AuthSession | null>(null);
const operatorToday = new Date().toLocaleDateString("sv-SE", { timeZone: "Africa/Cairo" });
const date = ref(typeof route.query.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date) ? route.query.date : operatorToday);
const includeUnpaid = ref(false);
const bookings = ref<Booking[]>([]);
const tours = ref<Record<string, Tour>>({});
const slots = ref<Record<string, TourSlot[]>>({});
const state = ref<"loading" | "loaded" | "error">("loading");
const errorKey = ref<ErpMessageKey | null>(null);
const errorMsg = computed(() => errorKey.value ? t(errorKey.value) : "");

const canSeeAssignments = computed(() => hasPermission(session.value, "tours-operator.assignment:manage"));
const assignmentViews = ref<Record<string, AssignmentView>>({});
const assignmentOptions = ref<AssignmentOptions | null>(null);
const canPrintOps = computed(() => hasPermission(session.value, "tours-operator.document:print-ops"));
const runs = computed(() => buildRunSheet(bookings.value, tours.value, slots.value, includeUnpaid.value));
const totalGuests = computed(() => runs.value.reduce((sum, run) => sum + run.guests, 0));
const unpaidCount = computed(() => bookings.value.filter(isUnpaid).length);
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;
const dateLabel = computed(() => formatDate(date.value, true));

async function fetchAllBookings(token: string, day: string): Promise<Booking[]> {
  const all: Booking[] = [];
  for (let page = 0; page < 20; page++) {
    const batch = await listBookings(token, { date: day, page, size: 200 });
    all.push(...batch);
    if (batch.length < 200) break;
  }
  return all;
}

// Each load is numbered; a slower answer for an earlier day is discarded.
let loadSeq = 0;

async function load() {
  if (!session.value || !ISO_DAY.test(date.value)) return;
  const seq = ++loadSeq;
  const day = date.value;
  state.value = "loading";
  errorKey.value = null;
  const token = session.value.token;
  try {
    const [dayBookings, allTours] = await Promise.all([
      fetchAllBookings(token, day),
      hasPermission(session.value, "tours-operator.tour:view") ? listAllStaffTours(token) : Promise.resolve([] as Tour[]),
    ]);
    if (seq !== loadSeq) return;
    bookings.value = dayBookings;
    tours.value = Object.fromEntries(allTours.map((t) => [t.id, t]));
    const tourIds = [...new Set(dayBookings.map((b) => b.tourId))];
    const slotLists = await Promise.all(tourIds.map((id) => listSlotsByDate(token, id, day).catch(() => [] as TourSlot[])));
    if (seq !== loadSeq) return;
    slots.value = Object.fromEntries(tourIds.map((id, i) => [id, slotLists[i]!]));
    await loadAssignments(token, day, seq);
    if (seq !== loadSeq) return;
    state.value = "loaded";
  } catch (err) {
    if (seq !== loadSeq) return;
    if (err instanceof ToursApiError && err.status === 401) {
      clearAuthSession();
      void router.replace("/login");
      return;
    }
    state.value = "error";
    errorKey.value = err instanceof ToursApiError && err.status === 403 ? "common.forbidden" : "today.loadFailed";
  }
}

/** Assignment data is optional: without the permission, or if it fails, the run sheet still loads. */
async function loadAssignments(token: string, day: string, seq: number) {
  if (!canSeeAssignments.value) return;
  try {
    const [views, options] = await Promise.all([listAssignments(token, day), getAssignmentOptions(token, day)]);
    if (seq !== loadSeq) return;
    assignmentViews.value = Object.fromEntries(views.map((v) => [v.slotId, v]));
    assignmentOptions.value = options;
  } catch {
    assignmentViews.value = {};
    assignmentOptions.value = null;
  }
}

function onAssigned(view: AssignmentView) {
  assignmentViews.value = { ...assignmentViews.value, [view.slotId]: view };
}

/** Another writer got there first: reload the day's assignments so the panel shows the latest revision. */
function onStale() {
  if (session.value) void loadAssignments(session.value.token, date.value, loadSeq);
}

/** "Tour name · time window" for a conflicting departure, from the day's own data. */
function slotLabel(slotId: string): string {
  const v = assignmentViews.value[slotId];
  return v ? `${v.tourNameEn} · ${t(`slot.${v.timeSlot}`)}` : t("asg.none");
}

function printSheet() {
  window.print();
}

function shiftDay(days: number) {
  const d = new Date(`${date.value}T00:00:00Z`);
  if (!Number.isFinite(d.getTime())) return;
  d.setUTCDate(d.getUTCDate() + days);
  date.value = d.toISOString().slice(0, 10);
}

watch(date, (value) => {
  // A cleared or partial date input must never load "all dates".
  if (!ISO_DAY.test(value)) return;
  void router.replace({ query: value === operatorToday ? {} : { date: value } });
  void load();
});

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) {
    void router.replace("/login");
    return;
  }
  void load();
});

const STATUS_TONE: Record<string, "success" | "warning" | "info" | "neutral"> = { CONFIRMED: "success", NEW: "warning", COMPLETED: "info" };
function unitsLabel(units: Record<string, number>, tourId: string): string {
  const options = tours.value[tourId]?.priceOptions ?? [];
  return Object.entries(units)
    .map(([code, quantity]) => `${count(quantity)} × ${options.find((o) => o.code === code)?.label ?? code}`)
    .join(" · ");
}
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-6xl">
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="text-sm font-semibold tracking-widest text-sts-muted uppercase">{{ t('today.runSheet') }}</p>
          <h1 class="mt-1 text-3xl font-semibold tracking-tight">{{ dateLabel }}</h1>
          <p v-if="state === 'loaded'" class="mt-1 text-sm text-sts-muted">
            {{ t('today.summary', { guests: count(totalGuests), tours: count(runs.length) }) }}<span v-if="unpaidCount"> · {{ t('common.awaitingCount', { count: count(unpaidCount) }) }}</span>
          </p>
        </div>
        <div class="flex flex-wrap items-center gap-2 print:hidden">
          <button type="button" class="rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm font-semibold" :aria-label="t('today.previous')" @click="shiftDay(-1)"><span aria-hidden="true" class="inline-block rtl:rotate-180">←</span></button>
          <label class="sr-only" for="run-date">{{ t('common.date') }}</label>
          <input id="run-date" v-model="date" type="date" class="rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm">
          <button type="button" class="rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm font-semibold" :aria-label="t('today.next')" @click="shiftDay(1)"><span aria-hidden="true" class="inline-block rtl:rotate-180">→</span></button>
          <button v-if="date !== operatorToday" type="button" class="rounded-lg px-3 py-2 text-sm font-semibold text-sts-ocean-mid hover:underline" @click="date = operatorToday">{{ t('nav.today') }}</button>
          <label class="ms-2 inline-flex items-center gap-2 text-sm">
            <input v-model="includeUnpaid" type="checkbox"> {{ t('today.showUnpaid') }}
          </label>
          <button type="button" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white" @click="printSheet">{{ t('today.print') }}</button>
          <NuxtLink v-if="canPrintOps" :to="documentPath('run-sheet', date)" class="rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm font-semibold">{{ docMessage(locale, 'doc.ui.printRunSheet') }}</NuxtLink>
        </div>
      </header>

      <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ errorMsg }}</WegoAlert>
      <p v-else-if="state === 'loading'" class="mt-8 text-sts-muted" role="status">{{ t('common.loading') }}</p>
      <p v-else-if="runs.length === 0" class="mt-8 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-8 text-center text-sts-muted">
        {{ t('today.empty') }}
      </p>

      <section v-for="run in runs" v-else :key="run.tourId" class="mt-8 break-inside-avoid" :aria-labelledby="`run-${run.tourId}`">
        <h2 :id="`run-${run.tourId}`" class="flex flex-wrap items-baseline gap-3 text-xl font-semibold">
          <span lang="en" dir="auto">{{ run.tourName }}</span> <span class="text-sm font-normal text-sts-muted">{{ t('common.guests', { count: count(run.guests) }) }}</span>
        </h2>
        <div v-for="departure in run.departures" :key="departure.timeSlot" class="mt-3 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface">
          <div class="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-sts-border bg-sts-canvas px-4 py-2 text-sm">
            <strong>{{ t(`slot.${departure.timeSlot}`) }}</strong>
            <span>{{ t('common.guests', { count: count(departure.guests) }) }}</span>
            <span v-if="departure.slot" class="text-sts-muted">{{ t('today.places', { booked: count(departure.slot.bookedCount), capacity: count(departure.slot.capacity) }) }}</span>
            <span v-if="Object.keys(departure.units).length" class="text-sts-muted">{{ unitsLabel(departure.units, run.tourId) }}</span>
            <NuxtLink v-if="canPrintOps && departure.slot" :to="documentPath('pickup', departure.slot.id)" class="font-semibold text-sts-ocean-mid hover:underline print:hidden">{{ docMessage(locale, 'doc.ui.printManifest') }}</NuxtLink>
            <WegoBadge v-if="departure.slot?.isBlocked" tone="danger">{{ t('today.blocked') }}</WegoBadge>
            <WegoBadge v-if="departure.unpaid" tone="warning">{{ t('common.awaitingCount', { count: count(departure.unpaid) }) }}</WegoBadge>
          </div>
          <AssignmentPanel
            v-if="canSeeAssignments && departure.slot && session"
            :token="session.token" :slot-id="departure.slot.id" :view="assignmentViews[departure.slot.id] ?? null"
            :options="assignmentOptions" :can-assign="canSeeAssignments" :can-print="canPrintOps" :slot-label="slotLabel"
            @saved="onAssigned" @stale="onStale"
          />
          <ul class="divide-y divide-sts-border md:hidden print:hidden">
            <li v-for="b in departure.bookings" :key="b.id" class="grid gap-1 px-4 py-3 text-sm">
              <div class="flex items-start justify-between gap-3">
                <NuxtLink :to="`/bookings/${b.id}`" class="font-semibold text-sts-ocean-mid hover:underline">{{ b.customer.fullName }}</NuxtLink>
                <span class="money tabular-nums">{{ money(b.totalPrice) }}</span>
              </div>
              <div class="ref font-mono text-xs text-sts-muted">{{ b.reference }} · {{ b.customer.nationality }}</div>
              <OfficePaymentBadge v-if="b.channel === 'OFFICE'" :booking="b" class="justify-self-start" />
              <WegoBadge v-else-if="b.status !== 'CONFIRMED'" :tone="STATUS_TONE[b.status] ?? 'neutral'" class="justify-self-start">{{ t(`status.${b.status}`) }}</WegoBadge>
              <div>{{ b.hotelName }}<span v-if="b.hotelRoom" class="text-sts-muted"> · {{ t('common.room', { room: b.hotelRoom }) }}</span></div>
              <div>
                {{ t('common.party', { adults: count(b.adultsCount), children: count(b.childrenCount) }) }}
                <span v-if="b.unit" class="text-sts-muted"> · {{ count(b.unit.unitCount) }} × {{ b.unit.optionLabel }}</span>
              </div>
              <div class="flex flex-wrap gap-3">
                <a :href="`tel:${b.customer.phone}`" dir="ltr" class="font-semibold hover:underline">{{ b.customer.phone }}</a>
                <a v-if="whatsappLink(b)" :href="whatsappLink(b)!" target="_blank" rel="noopener" class="font-semibold text-sts-success hover:underline">WhatsApp</a>
              </div>
              <p v-if="b.specialRequests" class="text-sts-muted">{{ b.specialRequests }}</p>
            </li>
          </ul>
          <div class="hidden overflow-x-auto md:block print:block">
            <table class="w-full text-sm">
              <thead class="text-start text-xs text-sts-muted">
                <tr>
                  <th scope="col" class="px-4 py-2 text-start">{{ t('today.guest') }}</th>
                  <th scope="col" class="px-4 py-2 text-start">{{ t('common.hotel') }}</th>
                  <th scope="col" class="px-4 py-2 text-start">{{ t('today.party') }}</th>
                  <th scope="col" class="px-4 py-2 text-start">{{ t('common.contact') }}</th>
                  <th scope="col" class="px-4 py-2 text-start">{{ t('common.notes') }}</th>
                  <th scope="col" class="px-4 py-2 text-end">{{ t('common.total') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in departure.bookings" :key="b.id" class="border-t border-sts-border align-top">
                  <td class="px-4 py-2">
                    <NuxtLink :to="`/bookings/${b.id}`" class="font-semibold text-sts-ocean-mid hover:underline">{{ b.customer.fullName }}</NuxtLink>
                    <div class="ref font-mono text-xs text-sts-muted">{{ b.reference }} · {{ b.customer.nationality }}</div>
                    <OfficePaymentBadge v-if="b.channel === 'OFFICE'" :booking="b" class="mt-1" />
                    <WegoBadge v-else-if="b.status !== 'CONFIRMED'" :tone="STATUS_TONE[b.status] ?? 'neutral'" class="mt-1">{{ t(`status.${b.status}`) }}</WegoBadge>
                  </td>
                  <td class="px-4 py-2">{{ b.hotelName }}<span v-if="b.hotelRoom" class="text-sts-muted"> · {{ t('common.room', { room: b.hotelRoom }) }}</span></td>
                  <td class="px-4 py-2 whitespace-nowrap">
                    {{ t('common.party', { adults: count(b.adultsCount), children: count(b.childrenCount) }) }}
                    <div v-if="b.unit" class="text-xs text-sts-muted">{{ count(b.unit.unitCount) }} × {{ b.unit.optionLabel }}</div>
                  </td>
                  <td class="px-4 py-2 whitespace-nowrap">
                    <a :href="`tel:${b.customer.phone}`" dir="ltr" class="hover:underline">{{ b.customer.phone }}</a>
                    <a v-if="whatsappLink(b)" :href="whatsappLink(b)!" target="_blank" rel="noopener" class="ms-2 text-xs font-semibold text-sts-success hover:underline print:hidden">WhatsApp</a>
                  </td>
                  <td class="max-w-xs px-4 py-2 text-sts-muted">{{ b.specialRequests }}</td>
                  <td class="money px-4 py-2 text-end tabular-nums">{{ money(b.totalPrice) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>
