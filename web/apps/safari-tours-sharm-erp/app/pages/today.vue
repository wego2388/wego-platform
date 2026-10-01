<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import type { Booking, Tour, TourSlot } from "@wego/api-contract";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { ToursApiError, formatMoney, listAllStaffTours, listBookings, listSlotsByDate } from "../composables/useToursApi";
import { whatsappLink } from "../composables/useWhatsApp";
import { buildRunSheet } from "../utils/runSheet";

useHead({ title: "Today · Safari Tours Sharm" });

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
const errorMsg = ref("");

const runs = computed(() => buildRunSheet(bookings.value, tours.value, slots.value, includeUnpaid.value));
const totalGuests = computed(() => runs.value.reduce((sum, run) => sum + run.guests, 0));
const unpaidCount = computed(() => bookings.value.filter((b) => b.status === "NEW").length);
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;
const dateLabel = computed(() =>
  !ISO_DAY.test(date.value) ? "" : new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC" }).format(new Date(`${date.value}T00:00:00Z`)),
);

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
  errorMsg.value = "";
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
    state.value = "loaded";
  } catch (err) {
    if (seq !== loadSeq) return;
    if (err instanceof ToursApiError && err.status === 401) {
      clearAuthSession();
      void router.replace("/login");
      return;
    }
    state.value = "error";
    errorMsg.value = err instanceof ToursApiError && err.status === 403 ? "You don't have permission to view bookings." : "Could not load the day's bookings.";
  }
}

function printSheet() {
  window.print();
}

function shiftDay(days: number) {
  const d = new Date(`${date.value}T00:00:00Z`);
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

const SLOT_LABEL: Record<string, string> = { SUNRISE: "Sunrise", MORNING: "Morning", AFTERNOON: "Afternoon", SUNSET: "Sunset" };
const STATUS_TONE: Record<string, "success" | "warning" | "info" | "neutral"> = { CONFIRMED: "success", NEW: "warning", COMPLETED: "info" };
function unitsLabel(units: Record<string, number>, tourId: string): string {
  const options = tours.value[tourId]?.priceOptions ?? [];
  return Object.entries(units)
    .map(([code, count]) => `${count} × ${options.find((o) => o.code === code)?.label ?? code}`)
    .join(" · ");
}
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-6xl">
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="text-sm font-semibold tracking-widest text-sts-muted uppercase">Run sheet</p>
          <h1 class="mt-1 text-3xl font-semibold tracking-tight">{{ dateLabel }}</h1>
          <p v-if="state === 'loaded'" class="mt-1 text-sm text-sts-muted">
            {{ totalGuests }} {{ totalGuests === 1 ? "guest" : "guests" }} on {{ runs.length }} {{ runs.length === 1 ? "tour" : "tours" }}<span v-if="unpaidCount"> · {{ unpaidCount }} awaiting payment</span>
          </p>
        </div>
        <div class="flex flex-wrap items-center gap-2 print:hidden">
          <button type="button" class="rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm font-semibold" aria-label="Previous day" @click="shiftDay(-1)">←</button>
          <label class="sr-only" for="run-date">Date</label>
          <input id="run-date" v-model="date" type="date" class="rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm">
          <button type="button" class="rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm font-semibold" aria-label="Next day" @click="shiftDay(1)">→</button>
          <button v-if="date !== operatorToday" type="button" class="rounded-lg px-3 py-2 text-sm font-semibold text-sts-ocean-mid hover:underline" @click="date = operatorToday">Today</button>
          <label class="ms-2 inline-flex items-center gap-2 text-sm">
            <input v-model="includeUnpaid" type="checkbox"> Show awaiting payment
          </label>
          <button type="button" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white" @click="printSheet">Print</button>
        </div>
      </header>

      <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ errorMsg }}</WegoAlert>
      <p v-else-if="state === 'loading'" class="mt-8 text-sts-muted" role="status">Loading…</p>
      <p v-else-if="runs.length === 0" class="mt-8 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-8 text-center text-sts-muted">
        No bookings for this day.
      </p>

      <section v-for="run in runs" v-else :key="run.tourId" class="mt-8 break-inside-avoid" :aria-labelledby="`run-${run.tourId}`">
        <h2 :id="`run-${run.tourId}`" class="flex flex-wrap items-baseline gap-3 text-xl font-semibold">
          {{ run.tourName }} <span class="text-sm font-normal text-sts-muted">{{ run.guests }} guests</span>
        </h2>
        <div v-for="departure in run.departures" :key="departure.timeSlot" class="mt-3 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface">
          <div class="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-sts-border bg-sts-canvas px-4 py-2 text-sm">
            <strong>{{ SLOT_LABEL[departure.timeSlot] }}</strong>
            <span>{{ departure.guests }} guests</span>
            <span v-if="departure.slot" class="text-sts-muted">places taken {{ departure.slot.bookedCount }} / {{ departure.slot.capacity }}</span>
            <span v-if="Object.keys(departure.units).length" class="text-sts-muted">{{ unitsLabel(departure.units, run.tourId) }}</span>
            <WegoBadge v-if="departure.slot?.isBlocked" tone="danger">Blocked</WegoBadge>
            <WegoBadge v-if="departure.unpaid" tone="warning">{{ departure.unpaid }} awaiting payment</WegoBadge>
          </div>
          <ul class="divide-y divide-sts-border md:hidden print:hidden">
            <li v-for="b in departure.bookings" :key="b.id" class="grid gap-1 px-4 py-3 text-sm">
              <div class="flex items-start justify-between gap-3">
                <NuxtLink :to="`/bookings/${b.id}`" class="font-semibold text-sts-ocean-mid hover:underline">{{ b.customer.fullName }}</NuxtLink>
                <span class="tabular-nums">{{ formatMoney(b.totalPrice) }}</span>
              </div>
              <div class="font-mono text-xs text-sts-muted">{{ b.reference }} · {{ b.customer.nationality }}</div>
              <WegoBadge v-if="b.status !== 'CONFIRMED'" :tone="STATUS_TONE[b.status] ?? 'neutral'" class="justify-self-start">{{ b.status === 'NEW' ? 'Awaiting payment' : b.status }}</WegoBadge>
              <div>{{ b.hotelName }}<span v-if="b.hotelRoom" class="text-sts-muted"> · room {{ b.hotelRoom }}</span></div>
              <div>
                {{ b.adultsCount }} ad<span v-if="b.childrenCount"> + {{ b.childrenCount }} ch</span>
                <span v-if="b.unit" class="text-sts-muted"> · {{ b.unit.unitCount }} × {{ b.unit.optionLabel }}</span>
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
                  <th scope="col" class="px-4 py-2 text-start">Guest</th>
                  <th scope="col" class="px-4 py-2 text-start">Hotel</th>
                  <th scope="col" class="px-4 py-2 text-start">Party</th>
                  <th scope="col" class="px-4 py-2 text-start">Contact</th>
                  <th scope="col" class="px-4 py-2 text-start">Notes</th>
                  <th scope="col" class="px-4 py-2 text-end">Total</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in departure.bookings" :key="b.id" class="border-t border-sts-border align-top">
                  <td class="px-4 py-2">
                    <NuxtLink :to="`/bookings/${b.id}`" class="font-semibold text-sts-ocean-mid hover:underline">{{ b.customer.fullName }}</NuxtLink>
                    <div class="font-mono text-xs text-sts-muted">{{ b.reference }} · {{ b.customer.nationality }}</div>
                    <WegoBadge v-if="b.status !== 'CONFIRMED'" :tone="STATUS_TONE[b.status] ?? 'neutral'" class="mt-1">{{ b.status === 'NEW' ? 'Awaiting payment' : b.status }}</WegoBadge>
                  </td>
                  <td class="px-4 py-2">{{ b.hotelName }}<span v-if="b.hotelRoom" class="text-sts-muted"> · room {{ b.hotelRoom }}</span></td>
                  <td class="px-4 py-2 whitespace-nowrap">
                    {{ b.adultsCount }} ad<span v-if="b.childrenCount"> + {{ b.childrenCount }} ch</span>
                    <div v-if="b.unit" class="text-xs text-sts-muted">{{ b.unit.unitCount }} × {{ b.unit.optionLabel }}</div>
                  </td>
                  <td class="px-4 py-2 whitespace-nowrap">
                    <a :href="`tel:${b.customer.phone}`" dir="ltr" class="hover:underline">{{ b.customer.phone }}</a>
                    <a v-if="whatsappLink(b)" :href="whatsappLink(b)!" target="_blank" rel="noopener" class="ms-2 text-xs font-semibold text-sts-success hover:underline print:hidden">WhatsApp</a>
                  </td>
                  <td class="max-w-xs px-4 py-2 text-sts-muted">{{ b.specialRequests }}</td>
                  <td class="px-4 py-2 text-end tabular-nums">{{ formatMoney(b.totalPrice) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>
