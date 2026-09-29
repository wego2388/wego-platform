<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import SiteHeader from "../../components/SiteHeader.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "../../content/locales";
import type { StsLocale } from "../../content/locales";
import {
  formatDate,
  formatTimeSlot,
  getPaymentStatus,
  peekLatestBookingConfirmation,
  takeLatestBookingConfirmation,
  type BookingConfirmation,
  type PaymentStatusResponse,
} from "../../composables/usePublicToursApi";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];
const locale    = useSiteLocale();
const copy      = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: "Booking Status — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
}));

const booking  = ref<BookingConfirmation | null>(null);
const payment  = ref<PaymentStatusResponse | null>(null);
const state    = ref<"loading" | "confirmed" | "unconfirmed" | "error">("loading");
const errorMsg = ref("");

onMounted(async () => {
  // sessionStorage supplies display-only booking details. It is mutable client
  // state and must never decide whether money was paid or a booking confirmed.
  const stored = peekLatestBookingConfirmation();
  if (!stored) {
    state.value = "error";
    errorMsg.value = "Booking details are no longer available in this tab. Use My Booking to retrieve them securely.";
    return;
  }

  try {
    const authoritativePayment = await getPaymentStatus(stored.id);
    if (authoritativePayment.bookingId !== stored.id) {
      throw new Error("Payment response did not match the stored booking");
    }
    payment.value = authoritativePayment;
    if (authoritativePayment.status !== "PAID") {
      state.value = "unconfirmed";
      errorMsg.value = authoritativePayment.status === "PENDING"
        ? "Your payment has not been confirmed yet. Please wait a moment or contact local support."
        : "This booking is not currently confirmed as paid. Please contact local support before making another payment.";
      return;
    }

    // Consume the tab-scoped display details only after backend-confirmed PAID truth.
    booking.value = takeLatestBookingConfirmation();
    state.value = "confirmed";
  } catch {
    state.value = "error";
    errorMsg.value = "We could not verify this payment. No confirmation has been issued. Please contact local support.";
  }
});

const reference = computed(() => booking.value?.reference ?? "");
const paidTotal = computed(() => {
  if (!payment.value) return "";
  const amount = Number(payment.value.amountEur);
  if (!Number.isFinite(amount)) return `${payment.value.currencyCode} ${payment.value.amountEur}`;
  return new Intl.NumberFormat(locale.value, {
    style: "currency",
    currency: payment.value.currencyCode,
  }).format(amount);
});

const whatsappConfirmUrl = computed(() => {
  const msg = encodeURIComponent(
    `Hi, I just made a booking with reference: ${reference.value}. Please confirm the booking details.`,
  );
  return `${whatsappUrl}?text=${msg}`;
});
</script>

<template>
  <div :dir="direction" :lang="locale" class="min-h-screen bg-sts-canvas text-sts-ink">
    <SiteHeader
      :locale="locale"
      :direction="direction"
      :nav="copy.nav"
      :whatsapp-label="copy.whatsappFab"
      :current-locales="ALL_LOCALES"
      @set-locale="(l) => (locale = l)"
    />

    <main id="main-content" tabindex="-1" class="mx-auto max-w-2xl px-6 py-16 text-center lg:px-0">

      <div v-if="state === 'loading'" class="text-sts-muted animate-pulse">Loading…</div>

      <template v-else-if="state === 'confirmed'">
        <!-- Success icon -->
        <div class="inline-grid size-20 place-items-center rounded-3xl bg-green-100 text-5xl shadow-sm" aria-hidden="true">
          ✅
        </div>

        <h1 class="mt-6 font-display text-3xl font-semibold text-green-700">
          Booking Confirmed!
        </h1>

        <p v-if="reference" class="mt-3 text-sts-muted">
          Your booking reference is
          <span class="font-mono font-bold text-sts-ink">{{ reference }}</span>
        </p>

        <!-- Payment confirmed notice -->
        <div
          v-if="payment?.status === 'PAID'"
          class="mt-4 inline-flex items-center gap-2 rounded-xl bg-green-50 border border-green-200 px-4 py-2 text-sm font-medium text-green-700"
        >
          ✅ Payment confirmed
        </div>

        <!-- Full booking summary if available -->
        <dl v-if="booking" class="mt-6 divide-y divide-sts-border rounded-2xl border border-sts-border bg-sts-surface text-left text-sm">
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Reference</dt>
            <dd class="font-mono font-bold">{{ booking.reference }}</dd>
          </div>
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Date</dt>
            <dd class="font-medium">{{ formatDate(booking.tourDate, locale) }}</dd>
          </div>
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Time</dt>
            <dd class="font-medium">{{ formatTimeSlot(booking.timeSlot) }}</dd>
          </div>
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Travelers</dt>
            <dd class="font-medium">
              {{ booking.adultsCount }} adult<span v-if="booking.adultsCount > 1">s</span>
              <span v-if="booking.childrenCount > 0">
                + {{ booking.childrenCount }} child<span v-if="booking.childrenCount > 1">ren</span>
              </span>
            </dd>
          </div>
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Total Paid</dt>
            <dd class="font-bold text-sts-ocean">{{ paidTotal }}</dd>
          </div>
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Hotel</dt>
            <dd class="font-medium">{{ booking.hotelName }}</dd>
          </div>
        </dl>

        <!-- What happens next -->
        <div class="mt-6 rounded-2xl border border-sts-border bg-sts-surface p-5 text-left text-sm">
          <h2 class="font-semibold text-sts-ink">What happens next?</h2>
          <ul class="mt-3 space-y-2 text-sts-muted">
            <li>📱 Keep your phone available for verified updates about this booking.</li>
            <li>🗺️ Confirm tour-specific inclusions and meeting details with local support.</li>
            <li>✅ Keep your reference number: <strong class="text-sts-ink font-mono">{{ reference }}</strong></li>
          </ul>
        </div>

        <!-- Actions -->
        <div class="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
          <a
            :href="whatsappConfirmUrl"
            target="_blank"
            rel="noopener"
            class="flex items-center justify-center gap-2 rounded-2xl bg-green-500 px-7 py-3.5 font-semibold text-white shadow transition-transform hover:-translate-y-0.5"
          >
            💬 Confirm on WhatsApp
          </a>
          <NuxtLink
            to="/tours"
            class="flex items-center justify-center rounded-2xl border border-sts-border px-7 py-3.5 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
          >
            Book Another Tour
          </NuxtLink>
        </div>

        <!-- My Booking link -->
        <p class="mt-6 text-sm text-sts-muted">
          Want to check your booking later?
          <NuxtLink to="/my-booking" class="text-sts-ocean hover:underline">
            My Booking →
          </NuxtLink>
        </p>
      </template>

      <div v-else-if="state === 'unconfirmed' || state === 'error'" class="rounded-3xl border border-amber-200 bg-amber-50 p-8 text-amber-950">
        <div class="text-5xl" aria-hidden="true">⚠️</div>
        <h1 class="mt-5 font-display text-2xl font-semibold">Payment is not confirmed</h1>
        <p class="mt-3 text-sm leading-6">{{ errorMsg }}</p>
        <div class="mt-7 flex flex-col gap-3 sm:flex-row sm:justify-center">
          <a
            :href="whatsappConfirmUrl"
            target="_blank"
            rel="noopener"
            class="inline-flex items-center justify-center rounded-2xl bg-green-500 px-6 py-3 font-semibold text-white"
          >
            Contact local support
          </a>
          <NuxtLink to="/my-booking" class="inline-flex items-center justify-center rounded-2xl border border-amber-300 px-6 py-3 font-semibold">
            Check My Booking
          </NuxtLink>
        </div>
      </div>

    </main>

    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
