<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { unitsNeeded, type TimeSlot, type Tour, type TourSlot } from "@wego/api-contract";
import { calculateBookingTotal, formatMoney, getAvailableSlots, multiplyMoney } from "../../composables/usePublicToursApi";
import { useDiscoveryCopy } from "../../composables/useDiscoveryCopy";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { tourPageCopy } from "../../content/tourPage";
import { whatsappUrl } from "../../content/locales";
import { BOOKING_WINDOW_DAYS, addDays, bookableByDay, operatorToday } from "../../utils/availability";
import { useSalesStatus } from "../../composables/useSalesStatus";
import { useAnalytics } from "../../composables/useAnalytics";
import { enquiryCopy } from "../../content/enquiry";
import { checkoutCopy } from "../../content/checkout";
import { onlineSalesAvailable, validPreferredDate } from "../../utils/enquiry";

/**
 * Date → time → guests → total → continue. Availability is always read live
 * in the browser (never from a cached page), and the checkout re-validates
 * everything server-side.
 */
const props = defineProps<{ tour: Tour; tourName: string; childrenAllowed: boolean | null }>();

const locale = useSiteLocale();
const copy = computed(() => tourPageCopy[locale.value].booking);
const discovery = useDiscoveryCopy();
const slotNames = computed(() => discovery.value.tours.slots);
const router = useRouter();
const localePath = useLocalePath();
// This card is inserted after hydration (desktop aside/mobile sheet). Keep
// setup synchronous; the shared SSR capability is already owned by the layout.
const { data: sales } = useSalesStatus();
const enquiry = computed(() => enquiryCopy[locale.value]);
const online = computed(() => onlineSalesAvailable(sales.value));
const analytics = useAnalytics();

const today = operatorToday();
const lastDay = addDays(today, BOOKING_WINDOW_DAYS);
const slots = ref<TourSlot[]>([]);
const state = ref<"loading" | "ready" | "error">("loading");
const selectedDate = ref<string | null>(null);
const selectedSlotId = ref<string | null>(null);
const preferredTime = ref<TimeSlot | null>(null);
const adults = ref(1);
const children = ref(0);
const requestLocked = ref(false);

async function load() {
  if (!online.value) return;
  state.value = "loading";
  try {
    slots.value = await getAvailableSlots(props.tour.id, today, lastDay);
    state.value = "ready";
  } catch {
    state.value = "error";
  }
}
onMounted(() => {
  watch(online, (enabled) => {
    selectedSlotId.value = null;
    slots.value = [];
    if (enabled) void load();
    else state.value = "ready";
  }, { immediate: true });
});

const perUnit = computed(() => props.tour.priceBasis === "PER_UNIT" && props.tour.priceOptions.length > 0);
const optionCode = ref<string | null>(props.tour.priceOptions[0]?.code ?? null);
const option = computed(() => props.tour.priceOptions.find((o) => o.code === optionCode.value) ?? null);
/** A departure is bookable when it has room for at least one unit (per unit) or one guest. */
const minPlaces = computed(() => (perUnit.value ? Math.min(...props.tour.priceOptions.map((o) => o.seatsPerUnit)) : 1));

const byDay = computed(() => bookableByDay(slots.value, today, minPlaces.value));
const daySlots = computed(() => (selectedDate.value ? byDay.value.get(selectedDate.value) ?? [] : []));
const selectedSlot = computed(() => daySlots.value.find((slot) => slot.id === selectedSlotId.value) ?? null);
watch(selectedDate, () => {
  selectedSlotId.value = daySlots.value.length === 1 ? daySlots.value[0]!.id : null;
});

const units = ref(1);
// Per-unit: children ride in the units like adults (no separate child price).
const childrenBookable = computed(() =>
  perUnit.value ? props.childrenAllowed !== false : props.childrenAllowed !== false && props.tour.priceChild !== null,
);
const guests = computed(() => adults.value + (childrenBookable.value ? children.value : 0));
/** Places left in the chosen departure (before one is chosen: the tour's capacity). */
const placesLeft = computed(() => selectedSlot.value?.available ?? props.tour.capacity);
// Per person every guest takes a place; per unit every seat of every unit
// bought does, so a solo rider still takes a whole buggy.
const unitsByPlaces = computed(() => (option.value ? Math.floor(placesLeft.value / option.value.seatsPerUnit) : 0));
const guestCap = computed(() => (perUnit.value && option.value ? unitsByPlaces.value * option.value.seatsPerUnit : placesLeft.value));
const unitsMin = computed(() => (option.value ? unitsNeeded(guests.value, option.value.seatsPerUnit) : 1));
const unitsMax = computed(() => Math.max(unitsMin.value, Math.min(guests.value, unitsByPlaces.value)));
// Always enough units for everyone, never more units than guests or room.
watch([unitsMin, unitsMax], ([min, max]) => {
  units.value = Math.min(Math.max(units.value, min), max);
}, { immediate: true });
function unitName(code: string, fallback: string) {
  return copy.value.unitNames[code] ?? fallback;
}
function perUnitLabel(code: string | undefined) {
  return (code && copy.value.perUnit[code]) || copy.value.perUnitDefault;
}
const adultsMax = computed(() => Math.max(1, guestCap.value - children.value));
const childrenMax = computed(() => Math.max(0, guestCap.value - adults.value));
watch(guestCap, (max) => {
  if (adults.value > max) adults.value = Math.max(1, max);
  if (adults.value + children.value > max) children.value = Math.max(0, max - adults.value);
});
/** Per unit: the chosen units must fit in the places left. */
const fits = computed(() => !perUnit.value || !option.value || units.value * option.value.seatsPerUnit <= placesLeft.value);

const total = computed(() => {
  if (perUnit.value) return option.value ? multiplyMoney(option.value.price, units.value) : null;
  try {
    return calculateBookingTotal(props.tour.priceAdult, adults.value, props.tour.priceChild, childrenBookable.value ? children.value : 0);
  } catch {
    return null;
  }
});
const canContinue = computed(() => selectedSlot.value !== null && total.value !== null && fits.value);
const preferredDateValid = computed(() => validPreferredDate(selectedDate.value, today));
const canRequest = computed(() => preferredDateValid.value && total.value !== null && fits.value);

function proceed() {
  const slot = selectedSlot.value;
  if (!slot || !online.value) return;
  void router.push({
    path: localePath(`/booking/${slot.id}`),
    query: {
      adults: String(adults.value),
      children: String(childrenBookable.value ? children.value : 0),
      ...(perUnit.value && option.value ? { option: option.value.code, units: String(units.value) } : {}),
      tourId: slot.tourId,
      date: slot.date,
      timeSlot: slot.timeSlot,
    },
  });
}

</script>

<template>
  <div class="grid gap-5">
    <div class="flex items-baseline justify-between gap-3">
      <h2 class="text-lg font-semibold">{{ online ? copy.heading : enquiry.title }}</h2>
      <p class="text-end">
        <span class="text-xs text-sts-muted">{{ copy.from }}</span>
        <span class="ms-1 text-2xl font-bold tabular-nums text-sts-ocean-bright">{{ formatMoney(tour.priceAdult) }}</span>
        <span class="ms-1 text-xs text-sts-muted">{{ perUnit ? perUnitLabel(tour.priceOptions[0]?.code) : copy.perPerson }}</span>
      </p>
    </div>

    <p v-if="tour.pricingNote" class="rounded-[var(--sts-radius-control)] bg-sts-sand-soft p-3 text-sm" lang="en">{{ tour.pricingNote }}</p>

    <fieldset :disabled="requestLocked" class="contents">
    <legend class="sr-only">{{ copy.heading }}</legend>
    <section v-if="!online" :aria-label="enquiry.dateLabel">
      <label :for="`preferred-date-${tour.id}`" class="mb-2 block text-sm font-bold">{{ enquiry.dateLabel }}</label>
      <input
        :id="`preferred-date-${tour.id}`" v-model="selectedDate" data-preferred-date type="date" :min="today" :max="addDays(today, 365)" required
        :aria-describedby="`preferred-date-help-${tour.id}`" :aria-invalid="!!selectedDate && !preferredDateValid"
        class="min-h-12 w-full min-w-0 rounded-[var(--sts-radius-control)] border border-sts-border bg-sts-surface px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sts-ocean-bright"
      >
      <p :id="`preferred-date-help-${tour.id}`" class="mt-2 text-xs text-sts-muted">{{ enquiry.dateHint }}</p>
      <p v-if="selectedDate && !preferredDateValid" class="mt-2 text-sm text-sts-danger" role="alert">{{ enquiry.dateInvalid }}</p>
      <label :for="`preferred-time-${tour.id}`" class="mb-2 mt-4 block text-sm font-bold">{{ enquiry.timeLabel }}</label>
      <select :id="`preferred-time-${tour.id}`" v-model="preferredTime" data-preferred-time class="min-h-12 w-full rounded-[var(--sts-radius-control)] border border-sts-border bg-sts-surface px-3 py-2 text-sm">
        <option :value="null">{{ enquiry.anyTime }}</option>
        <option v-for="time in tour.availableTimeSlots" :key="time" :value="time">{{ slotNames[time] }}</option>
      </select>
    </section>
    <section v-else :aria-label="copy.date">
      <h3 class="mb-2 text-sm font-bold">{{ copy.date }}</h3>
      <div v-if="state === 'loading'" class="grid gap-2" role="status">
        <span class="sr-only">{{ copy.loading }}</span>
        <UiSkeleton class="h-8 w-full" />
        <UiSkeleton class="h-56 w-full" />
      </div>
      <div v-else-if="state === 'error'" class="grid justify-items-start gap-2 text-sm">
        <p class="text-sts-danger">{{ copy.loadError }}</p>
        <UiButton variant="secondary" size="sm" icon="lucide:refresh-cw" @click="load">{{ copy.retry }}</UiButton>
      </div>
      <div v-else-if="byDay.size === 0" class="rounded-[var(--sts-radius-control)] border border-dashed border-sts-border p-4 text-sm">
        <p class="font-semibold">{{ copy.noDatesTitle }}</p>
        <p class="mt-1 text-sts-muted">{{ copy.noDatesBody }}</p>
      </div>
      <TourAvailabilityCalendar v-else v-model="selectedDate" :by-day="byDay" :today="today" :last-day="lastDay" />
    </section>

    <section v-if="online && selectedDate" :aria-label="copy.time">
      <h3 class="mb-2 text-sm font-bold">{{ copy.time }}</h3>
      <fieldset class="flex flex-wrap gap-2">
        <legend class="sr-only">{{ copy.time }}</legend>
        <label
          v-for="slot in daySlots"
          :key="slot.id"
          class="flex min-h-11 cursor-pointer flex-col items-start rounded-[var(--sts-radius-control)] border px-4 py-2 text-start text-sm transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-sts-ocean-bright"
          :class="slot.id === selectedSlotId ? 'border-sts-ocean bg-sts-ocean text-white' : 'border-sts-border bg-sts-surface hover:border-sts-ocean-bright'"
        >
          <input v-model="selectedSlotId" type="radio" class="sr-only" :name="`slot-${tour.id}`" :value="slot.id">
          <span class="font-semibold">{{ slotNames[slot.timeSlot] }}</span>
          <span class="text-xs opacity-80">{{ copy.left(slot.available) }}</span>
        </label>
      </fieldset>
    </section>
    <p v-else-if="online && state === 'ready' && byDay.size" class="text-sm text-sts-muted">{{ copy.pickDate }}</p>

    <fieldset v-if="perUnit && tour.priceOptions.length > 1" class="grid gap-2 border-t border-sts-border pt-4">
      <legend class="mb-2 text-sm font-bold">{{ copy.option }}</legend>
      <label
        v-for="o in tour.priceOptions"
        :key="o.code"
        class="flex min-h-12 cursor-pointer items-center justify-between gap-3 rounded-[var(--sts-radius-control)] border px-4 py-2 text-sm transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-sts-ocean-bright"
        :class="o.code === optionCode ? 'border-sts-ocean bg-sts-ocean text-white' : 'border-sts-border bg-sts-surface hover:border-sts-ocean-bright'"
      >
        <input v-model="optionCode" type="radio" class="sr-only" :name="`option-${tour.id}`" :value="o.code">
        <span>
          <span class="block font-semibold">{{ unitName(o.code, o.label) }}</span>
          <span class="text-xs opacity-80">{{ copy.seats(o.seatsPerUnit) }}</span>
        </span>
        <span class="font-bold tabular-nums">{{ formatMoney(o.price) }}</span>
      </label>
    </fieldset>
    <p v-else-if="perUnit && option" class="border-t border-sts-border pt-4 text-sm">
      <span class="font-semibold">{{ unitName(option.code, option.label) }}</span>
      <span class="text-sts-muted"> · {{ copy.seats(option.seatsPerUnit) }}</span>
    </p>

    <section :aria-label="copy.guests" class="grid gap-3 border-t border-sts-border pt-4">
      <UiStepper v-model="adults" :label="copy.adults" :min="1" :max="adultsMax" />
      <UiStepper v-if="childrenBookable" v-model="children" :label="copy.children" :min="0" :max="childrenMax" />
      <p v-else-if="childrenAllowed === false" class="text-xs text-sts-muted">{{ copy.childrenNotAllowed }}</p>
      <p v-else class="text-xs text-sts-muted">{{ copy.childPriceOnRequest }}</p>
      <UiStepper v-if="perUnit && option" v-model="units" :label="copy.units" :hint="perUnitLabel(option.code)" :min="unitsMin" :max="unitsMax" />
    </section>

    <div v-if="(online ? selectedSlot : preferredDateValid) && total" class="rounded-[var(--sts-radius-control)] bg-sts-sand-soft p-4 text-sm">
      <p v-if="perUnit && option" class="flex justify-between text-sts-muted">
        <span>{{ copy.unitLine(units, unitName(option.code, option.label)) }} × {{ formatMoney(option.price) }}</span>
        <span class="tabular-nums">{{ formatMoney(total) }}</span>
      </p>
      <p v-if="!perUnit" class="flex justify-between text-sts-muted">
        <span>{{ copy.adultLine(adults) }} × {{ formatMoney(tour.priceAdult) }}</span>
        <span class="tabular-nums">{{ formatMoney(multiplyMoney(tour.priceAdult, adults)) }}</span>
      </p>
      <p v-if="!perUnit && childrenBookable && children > 0 && tour.priceChild" class="flex justify-between text-sts-muted">
        <span>{{ copy.childLine(children) }} × {{ formatMoney(tour.priceChild) }}</span>
        <span class="tabular-nums">{{ formatMoney(multiplyMoney(tour.priceChild, children)) }}</span>
      </p>
      <p class="mt-2 flex justify-between border-t border-sts-border pt-2 text-base font-bold">
        <span>{{ online ? copy.total : enquiry.estimatedTotal }}</span>
        <span class="tabular-nums">{{ formatMoney(total) }}</span>
      </p>
    </div>

    </fieldset>
    <div class="grid gap-2">
      <UiButton v-if="online" size="lg" block :disabled="!canContinue" icon-end="lucide:arrow-right" @click="proceed">{{ copy.continue }}</UiButton>
      <template v-else>
        <TourRequestForm
v-if="sales?.bookingMode === 'ENQUIRY_ONLY'" :valid="canRequest" :selection="{
          tourId: tour.id, preferredDate: selectedDate ?? '', adultsCount: adults,
          childrenCount: childrenBookable ? children : 0,
          ...(preferredTime ? { preferredTime } : {}),
          ...(perUnit && option ? { priceOptionCode: option.code, unitCount: units } : {}),
        }" @locked="requestLocked = $event" />
        <p v-else class="rounded-[var(--sts-radius-control)] bg-sts-sand-soft p-3 text-sm" data-enquiry-notice>{{ sales ? checkoutCopy[locale].errors.salesPaused : enquiry.unknown }}</p>
      </template>
      <p v-if="online && selectedDate && !selectedSlot" class="text-center text-xs text-sts-muted">{{ copy.pickTime }}</p>
      <UiButton :href="whatsappUrl" variant="ghost" block icon="lucide:message-circle" @click="analytics.track('whatsapp_click', { placement: 'tour_questions', item_id: tour.slug })">{{ copy.askWhatsapp }}</UiButton>
    </div>
  </div>
</template>
