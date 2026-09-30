<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type { Tour, TourSlot } from "@wego/api-contract";
import { calculateBookingTotal, formatMoney, getAvailableSlots, multiplyMoney } from "../../composables/usePublicToursApi";
import { useDiscoveryCopy } from "../../composables/useDiscoveryCopy";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { tourPageCopy } from "../../content/tourPage";
import { whatsappUrl } from "../../content/locales";
import { BOOKING_WINDOW_DAYS, addDays, bookableByDay, operatorToday } from "../../utils/availability";

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

const today = operatorToday();
const lastDay = addDays(today, BOOKING_WINDOW_DAYS);
const slots = ref<TourSlot[]>([]);
const state = ref<"loading" | "ready" | "error">("loading");
const selectedDate = ref<string | null>(null);
const selectedSlotId = ref<string | null>(null);
const adults = ref(1);
const children = ref(0);

async function load() {
  state.value = "loading";
  try {
    slots.value = await getAvailableSlots(props.tour.id, today, lastDay);
    state.value = "ready";
  } catch {
    state.value = "error";
  }
}
onMounted(load);

const byDay = computed(() => bookableByDay(slots.value, today));
const daySlots = computed(() => (selectedDate.value ? byDay.value.get(selectedDate.value) ?? [] : []));
const selectedSlot = computed(() => daySlots.value.find((slot) => slot.id === selectedSlotId.value) ?? null);
watch(selectedDate, () => {
  selectedSlotId.value = daySlots.value.length === 1 ? daySlots.value[0]!.id : null;
});

const isTransfer = computed(() => props.tour.tourType === "TRANSFER");
const childrenBookable = computed(() => props.childrenAllowed !== false && props.tour.priceChild !== null && !isTransfer.value);
const seats = computed(() => selectedSlot.value?.available ?? props.tour.capacity);
const adultsMax = computed(() => Math.max(1, seats.value - children.value));
const childrenMax = computed(() => Math.max(0, seats.value - adults.value));
watch(seats, (max) => {
  if (adults.value > max) adults.value = Math.max(1, max);
  if (adults.value + children.value > max) children.value = Math.max(0, max - adults.value);
});

const total = computed(() => {
  try {
    return calculateBookingTotal(props.tour.priceAdult, adults.value, props.tour.priceChild, childrenBookable.value ? children.value : 0);
  } catch {
    return null;
  }
});
const canContinue = computed(() => selectedSlot.value !== null && total.value !== null);

function proceed() {
  const slot = selectedSlot.value;
  if (!slot) return;
  void router.push({
    path: localePath(`/booking/${slot.id}`),
    query: {
      adults: String(adults.value),
      children: String(childrenBookable.value ? children.value : 0),
      tourId: slot.tourId,
      date: slot.date,
      timeSlot: slot.timeSlot,
    },
  });
}

const whatsappLink = computed(() => {
  const text = copy.value.whatsappMessage(props.tourName, selectedDate.value, adults.value, children.value);
  return `${whatsappUrl}?text=${encodeURIComponent(text)}`;
});
</script>

<template>
  <div class="grid gap-5">
    <div class="flex items-baseline justify-between gap-3">
      <h2 class="text-lg font-semibold">{{ copy.heading }}</h2>
      <p class="text-end">
        <span class="text-xs text-sts-muted">{{ copy.from }}</span>
        <span class="ms-1 text-2xl font-bold tabular-nums text-sts-ocean-bright">{{ formatMoney(tour.priceAdult) }}</span>
        <span class="ms-1 text-xs text-sts-muted">{{ isTransfer ? copy.perVehicle : copy.perPerson }}</span>
      </p>
    </div>

    <p v-if="tour.pricingNote" class="rounded-[var(--sts-radius-control)] bg-sts-sand-soft p-3 text-sm" lang="en">{{ tour.pricingNote }}</p>

    <section :aria-label="copy.date">
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

    <section v-if="selectedDate" :aria-label="copy.time">
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
    <p v-else-if="state === 'ready' && byDay.size" class="text-sm text-sts-muted">{{ copy.pickDate }}</p>

    <section :aria-label="copy.guests" class="grid gap-3 border-t border-sts-border pt-4">
      <UiStepper v-model="adults" :label="copy.adults" :min="1" :max="adultsMax" />
      <UiStepper v-if="childrenBookable" v-model="children" :label="copy.children" :min="0" :max="childrenMax" />
      <p v-else-if="childrenAllowed === false" class="text-xs text-sts-muted">{{ copy.childrenNotAllowed }}</p>
      <p v-else-if="!isTransfer" class="text-xs text-sts-muted">{{ copy.childPriceOnRequest }}</p>
    </section>

    <div v-if="selectedSlot && total" class="rounded-[var(--sts-radius-control)] bg-sts-sand-soft p-4 text-sm">
      <p class="flex justify-between text-sts-muted">
        <span>{{ copy.adultLine(adults) }} × {{ formatMoney(tour.priceAdult) }}</span>
        <span class="tabular-nums">{{ formatMoney(multiplyMoney(tour.priceAdult, adults)) }}</span>
      </p>
      <p v-if="childrenBookable && children > 0 && tour.priceChild" class="flex justify-between text-sts-muted">
        <span>{{ copy.childLine(children) }} × {{ formatMoney(tour.priceChild) }}</span>
        <span class="tabular-nums">{{ formatMoney(multiplyMoney(tour.priceChild, children)) }}</span>
      </p>
      <p class="mt-2 flex justify-between border-t border-sts-border pt-2 text-base font-bold">
        <span>{{ copy.total }}</span>
        <span class="tabular-nums">{{ formatMoney(total) }}</span>
      </p>
    </div>

    <div class="grid gap-2">
      <UiButton size="lg" block :disabled="!canContinue" icon-end="lucide:arrow-right" @click="proceed">{{ copy.continue }}</UiButton>
      <p v-if="selectedDate && !selectedSlot" class="text-center text-xs text-sts-muted">{{ copy.pickTime }}</p>
      <UiButton :href="whatsappLink" variant="ghost" block icon="lucide:message-circle">{{ copy.askWhatsapp }}</UiButton>
    </div>
  </div>
</template>
