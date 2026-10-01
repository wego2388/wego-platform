<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { formatMoney } from "@wego/api-contract";
import { useCatalog } from "../../composables/useCatalog";
import { useAnalytics } from "../../composables/useAnalytics";
import { useDiscoveryCopy } from "../../composables/useDiscoveryCopy";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { bookingResultCopy, buildCalendarFile } from "../../content/bookingResult";
import { whatsappUrl } from "../../content/locales";
import { tourPageCopy } from "../../content/tourPage";
import {
  getPaymentStatus,
  peekLatestBookingConfirmation,
  takeLatestBookingConfirmation,
  type BookingConfirmation,
  type PaymentStatusResponse,
} from "../../composables/usePublicToursApi";

/**
 * The booking "ticket". sessionStorage only supplies display details; the
 * server's payment status alone decides whether this page says confirmed.
 */
const locale = useSiteLocale();
const analytics = useAnalytics();
const copy = computed(() => bookingResultCopy[locale.value]);
const tourCopy = computed(() => tourPageCopy[locale.value].booking);
const discovery = useDiscoveryCopy();
const intlLocale = computed(() => (locale.value === "ar" ? "ar-EG" : locale.value));
useSeoMeta({ title: () => `${copy.value.confirmed.ticket} — Safari Tours Sharm`, robots: "noindex" });

const booking = ref<BookingConfirmation | null>(null);
const payment = ref<PaymentStatusResponse | null>(null);
const state = ref<"loading" | "confirmed" | "unconfirmed" | "gone">("loading");
const message = ref("");

onMounted(async () => {
  const stored = peekLatestBookingConfirmation();
  if (!stored) {
    // Nothing in this tab (e.g. a refresh after the confirmation was shown):
    // this says nothing about payment, so it must not read as "not paid".
    state.value = "gone";
    return;
  }
  booking.value = stored;
  try {
    const authoritative = await getPaymentStatus(stored.id);
    if (authoritative.bookingId !== stored.id) throw new Error("Payment response did not match the stored booking");
    payment.value = authoritative;
    if (authoritative.status !== "PAID") {
      state.value = "unconfirmed";
      message.value = authoritative.status === "PENDING" ? copy.value.unconfirmed.pending : copy.value.unconfirmed.notPaid;
      return;
    }
    // Show the booking that was checked; clear the tab-scoped copy only now.
    booking.value = stored;
    takeLatestBookingConfirmation();
    state.value = "confirmed";
    // Sent once: the tab-scoped booking was just consumed, so a refresh cannot repeat it.
    analytics.track("purchase", { transaction_id: stored.reference, value: Number(authoritative.amountEur), currency: authoritative.currencyCode });
  } catch {
    state.value = "unconfirmed";
    message.value = copy.value.unconfirmed.verify;
  }
});

const { data: catalog } = useCatalog();
const tourName = computed(() => (catalog.value ?? []).find((e) => e.tour.id === booking.value?.tourId)?.name ?? null);
const reference = computed(() => booking.value?.reference ?? "");
const dateLabel = computed(() =>
  booking.value
    ? new Intl.DateTimeFormat(intlLocale.value, { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC" }).format(
        new Date(`${booking.value.tourDate}T00:00:00Z`),
      )
    : "",
);
const guests = computed(() =>
  booking.value
    ? [tourCopy.value.adultLine(booking.value.adultsCount), booking.value.childrenCount ? tourCopy.value.childLine(booking.value.childrenCount) : null]
        .filter(Boolean)
        .join(" · ")
    : "",
);
const unitLabel = computed(() => {
  const unit = booking.value?.unit;
  return unit ? tourCopy.value.unitLine(unit.unitCount, tourCopy.value.unitNames[unit.optionCode] ?? unit.optionLabel) : null;
});
const paidTotal = computed(() => {
  if (!payment.value) return "";
  return formatMoney({ amount: payment.value.amountEur, currencyCode: payment.value.currencyCode });
});
const whatsappLink = computed(() => {
  const text = state.value === "confirmed" ? copy.value.whatsapp.confirm(reference.value) : copy.value.whatsapp.paymentHelp(reference.value || "—");
  return `${whatsappUrl}?text=${encodeURIComponent(text)}`;
});

function downloadCalendar() {
  if (!booking.value) return;
  const ics = buildCalendarFile({
    uid: booking.value.reference,
    date: booking.value.tourDate,
    title: copy.value.calendarTitle(tourName.value ?? "Tour"),
    description: `${copy.value.confirmed.reference}: ${booking.value.reference}`,
  });
  const url = URL.createObjectURL(new Blob([ics], { type: "text/calendar;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `${booking.value.reference}.ics`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
</script>

<template>
  <main id="main-content" tabindex="-1" class="mx-auto max-w-xl px-4 py-12 sm:px-6">
    <div v-if="state === 'loading'" class="grid gap-3" role="status">
      <UiSkeleton class="mx-auto h-16 w-16 rounded-full" />
      <UiSkeleton class="h-8 w-2/3 justify-self-center" />
      <UiSkeleton class="h-56 w-full" />
    </div>

    <template v-else-if="state === 'confirmed'">
      <div class="text-center">
        <span class="mx-auto grid size-16 place-items-center rounded-full bg-sts-success-soft text-sts-success" aria-hidden="true">
          <Icon name="lucide:circle-check" class="size-9" />
        </span>
        <h1 class="mt-5 font-display text-3xl font-semibold">{{ copy.confirmed.title }}</h1>
        <p class="mt-2 inline-flex items-center gap-1.5 rounded-full bg-sts-success-soft px-3 py-1 text-sm font-semibold text-sts-success">
          <Icon name="lucide:badge-check" class="size-4" aria-hidden="true" />{{ copy.confirmed.paid }}
        </p>
      </div>

      <section class="mt-8 overflow-hidden rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface shadow-sts-raised" :aria-label="copy.confirmed.ticket">
        <div class="bg-sts-ocean px-6 py-5 text-white">
          <p class="text-xs font-semibold tracking-wide text-white/70 uppercase">{{ copy.confirmed.reference }}</p>
          <p class="mt-1 font-mono text-3xl font-bold tracking-wider" dir="ltr">{{ reference }}</p>
          <p v-if="tourName" class="mt-2 font-semibold">{{ tourName }}</p>
        </div>
        <dl v-if="booking" class="grid gap-3 px-6 py-5 text-sm">
          <div class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.confirmed.date }}</dt><dd class="text-end font-medium">{{ dateLabel }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.confirmed.time }}</dt><dd class="font-medium">{{ discovery.tours.slots[booking.timeSlot] ?? booking.timeSlot }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.confirmed.guests }}</dt><dd class="text-end font-medium">{{ guests }}</dd></div>
          <div v-if="unitLabel" class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.confirmed.units }}</dt><dd class="text-end font-medium">{{ unitLabel }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.confirmed.hotel }}</dt><dd class="text-end font-medium">{{ booking.hotelName }}</dd></div>
          <div class="flex justify-between gap-3 border-t border-dashed border-sts-border pt-3 text-base font-bold"><dt>{{ copy.confirmed.total }}</dt><dd class="tabular-nums">{{ paidTotal }}</dd></div>
        </dl>
      </section>

      <section class="mt-6 rounded-[var(--sts-radius-card)] bg-sts-sand-soft p-5 text-sm" aria-labelledby="next-heading">
        <h2 id="next-heading" class="font-semibold">{{ copy.confirmed.nextTitle }}</h2>
        <ol class="mt-3 grid gap-2">
          <li v-for="(line, i) in copy.confirmed.next" :key="line" class="flex gap-3">
            <span class="grid size-6 shrink-0 place-items-center rounded-full bg-sts-ocean text-xs font-bold text-white">{{ i + 1 }}</span>
            <span>{{ line }}</span>
          </li>
        </ol>
      </section>

      <div class="mt-6 grid gap-3 sm:grid-cols-2">
        <UiButton :href="whatsappLink" icon="lucide:message-circle" block>{{ copy.support }}</UiButton>
        <UiButton variant="secondary" icon="lucide:calendar-plus" block @click="downloadCalendar">{{ copy.confirmed.addCalendar }}</UiButton>
      </div>
      <p class="mt-6 text-center text-sm text-sts-muted">
        {{ copy.confirmed.later }}
        <NuxtLinkLocale to="/my-booking" class="font-semibold text-sts-ocean-bright underline">{{ copy.confirmed.myBooking }}</NuxtLinkLocale>
        · <NuxtLinkLocale to="/tours" class="font-semibold text-sts-ocean-bright underline">{{ copy.confirmed.another }}</NuxtLinkLocale>
      </p>
    </template>

    <div v-else-if="state === 'gone'" class="rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-8 text-center">
      <span class="mx-auto grid size-14 place-items-center rounded-full bg-sts-sand-soft text-sts-ocean-bright" aria-hidden="true">
        <Icon name="lucide:ticket" class="size-8" />
      </span>
      <h1 class="mt-5 font-display text-2xl font-semibold">{{ copy.unconfirmed.findTitle }}</h1>
      <p class="mt-3 text-sm leading-6">{{ copy.unconfirmed.gone }}</p>
      <div class="mt-7 flex flex-wrap justify-center gap-3">
        <UiButton to="/my-booking">{{ copy.unconfirmed.check }}</UiButton>
        <UiButton :href="whatsappLink" variant="secondary" icon="lucide:message-circle">{{ copy.support }}</UiButton>
      </div>
    </div>

    <div v-else class="rounded-[var(--sts-radius-card)] border border-sts-warning/40 bg-sts-warning-soft p-8 text-center">
      <span class="mx-auto grid size-14 place-items-center rounded-full bg-sts-surface text-sts-warning" aria-hidden="true">
        <Icon name="lucide:triangle-alert" class="size-8" />
      </span>
      <h1 class="mt-5 font-display text-2xl font-semibold">{{ copy.unconfirmed.title }}</h1>
      <p class="mt-3 text-sm leading-6">{{ message }}</p>
      <div class="mt-7 flex flex-wrap justify-center gap-3">
        <UiButton :href="whatsappLink" icon="lucide:message-circle">{{ copy.support }}</UiButton>
        <UiButton to="/my-booking" variant="secondary">{{ copy.unconfirmed.check }}</UiButton>
      </div>
    </div>
  </main>
</template>
