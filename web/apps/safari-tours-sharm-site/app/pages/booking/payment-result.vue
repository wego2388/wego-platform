<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { whatsappUrl } from "../../content/locales";
import {
  getPaymentStatus,
  peekLatestBookingConfirmation,
  PublicApiError,
  type BookingConfirmation,
  type PaymentStatusResponse,
} from "../../composables/usePublicToursApi";

/**
 * Payment result page — shown after the customer returns from Paymob checkout.
 *
 * Paymob redirects here with ?success=true|false&id=<transactionId>&order=<orderId>
 *
 * We DO NOT trust the success query param from Paymob — instead we poll
 * our own server for the authoritative payment status.
 * This prevents a customer from manually changing ?success=false to true.
 */

const router = useRouter();
const localePath = useLocalePath();


useHead(() => ({
  title: "Payment Result — Safari Tours Sharm",
}));

type PageState = "loading" | "confirmed" | "failed" | "pending" | "review" | "error";

const state        = ref<PageState>("loading");
const booking      = ref<BookingConfirmation | null>(null);
const paymentState = ref<PaymentStatusResponse | null>(null);
const errorMsg     = ref("");

const bookingId  = computed(() => booking.value?.id);
const bookingRef = computed(() => booking.value?.reference);

// Maximum number of polls before giving up (5s × 12 = 60s total)
const MAX_POLLS = 12;
const POLL_INTERVAL_MS = 5000;

onMounted(async () => {
  // Paymob may append its own transaction fields to the return URL, but Wego's
  // booking id/reference stay in this tab's session storage and never in it.
  booking.value = peekLatestBookingConfirmation();
  if (!bookingId.value) {
    state.value = "error";
    errorMsg.value = "Missing booking information. Please check your booking via WhatsApp.";
    return;
  }

  // Poll server for authoritative payment status
  await pollPaymentStatus();
});

async function pollPaymentStatus(attempt = 0): Promise<void> {
  if (attempt >= MAX_POLLS) {
    // Timed out — show pending state with WhatsApp fallback
    state.value = "pending";
    return;
  }

  try {
    const status = await getPaymentStatus(bookingId.value!);
    paymentState.value = status;

    switch (status.status) {
      case "PAID":
        state.value = "confirmed";
        // Redirect to the full confirmation page
        void router.replace(localePath("/booking/confirmation"));
        return;

      case "FAILED":
        state.value = "failed";
        return;

      case "PENDING":
        // Webhook hasn't arrived yet — wait and retry
        await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
        return pollPaymentStatus(attempt + 1);

      case "REFUNDED":
      case "REVIEW_REQUIRED":
      case "RECONCILIATION_REQUIRED":
        // Never claim success/failure while the provider outcome is ambiguous.
        state.value = "review";
        return;
    }
  } catch (err) {
    if (err instanceof PublicApiError && err.status === 404) {
      // Payment record not created yet — retry
      await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
      return pollPaymentStatus(attempt + 1);
    }
    state.value = "error";
    errorMsg.value = "Could not retrieve payment status. Please contact us on WhatsApp.";
  }
}

const whatsappHelpUrl = computed(() => {
  const ref = bookingRef.value ?? "unknown";
  const msg = encodeURIComponent(
    `Hi, I just tried to pay for booking ${ref} but I'm not sure if the payment went through. Can you help?`,
  );
  return `${whatsappUrl}?text=${msg}`;
});
</script>

<template>
  <div>

    <main id="main-content" tabindex="-1" class="mx-auto max-w-2xl px-6 py-16 text-center lg:px-0">

      <!-- Loading / polling ──────────────────────────────────── -->
      <template v-if="state === 'loading' || state === 'pending'">
        <div class="size-16 animate-spin rounded-full border-4 border-sts-ocean border-t-transparent mx-auto" aria-hidden="true" />
        <h1 class="mt-6 text-2xl font-semibold text-sts-ink">
          {{ state === 'loading' ? 'Checking payment…' : 'Waiting for payment confirmation…' }}
        </h1>
        <p class="mt-3 text-sm text-sts-muted">
          This usually takes a few seconds. Please do not close this page.
        </p>

        <div v-if="state === 'pending'" class="mt-8">
          <p class="text-sm text-sts-muted">Taking longer than expected?</p>
          <a
            :href="whatsappHelpUrl"
            target="_blank"
            rel="noopener"
            class="mt-3 inline-flex items-center gap-2 rounded-2xl bg-green-500 px-6 py-3 font-semibold text-white shadow"
          >
            💬 Contact us on WhatsApp
          </a>
        </div>
      </template>

      <!-- Confirmed — redirecting ─────────────────────────────── -->
      <template v-else-if="state === 'confirmed'">
        <div class="inline-grid size-20 place-items-center rounded-3xl bg-green-100 text-5xl shadow-sm" aria-hidden="true">
          ✅
        </div>
        <h1 class="mt-6 font-display text-3xl font-semibold text-green-700">
          Payment Confirmed!
        </h1>
        <p class="mt-3 text-sts-muted">Redirecting to your booking details…</p>
      </template>

      <!-- Failed ──────────────────────────────────────────────── -->
      <template v-else-if="state === 'failed'">
        <div class="inline-grid size-20 place-items-center rounded-3xl bg-red-100 text-5xl shadow-sm" aria-hidden="true">
          ❌
        </div>
        <h1 class="mt-6 font-display text-3xl font-semibold text-red-700">
          Payment Failed
        </h1>
        <p class="mt-3 text-sts-muted">
          Your payment was not completed. Your slot reservation will be released shortly.
        </p>

        <div class="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
          <!-- Retry — go back to the booking page -->
          <NuxtLinkLocale
            to="/tours"
            class="flex items-center justify-center rounded-2xl border border-sts-border px-7 py-3.5 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
          >
            Try Again
          </NuxtLinkLocale>
          <a
            :href="whatsappHelpUrl"
            target="_blank"
            rel="noopener"
            class="flex items-center justify-center gap-2 rounded-2xl bg-green-500 px-7 py-3.5 font-semibold text-white shadow"
          >
            💬 Book via WhatsApp
          </a>
        </div>
      </template>

      <!-- Provider outcome requires reconciliation ───────────── -->
      <template v-else-if="state === 'review'">
        <div class="inline-grid size-20 place-items-center rounded-3xl bg-amber-100 text-5xl shadow-sm" aria-hidden="true">
          ⚠️
        </div>
        <h1 class="mt-6 font-display text-3xl font-semibold text-amber-800">
          We’re checking your payment
        </h1>
        <p class="mt-3 text-sts-muted">
          We cannot safely confirm the final payment result yet. Please do not pay again until local support checks it.
        </p>
        <a
          :href="whatsappHelpUrl"
          target="_blank"
          rel="noopener"
          class="mt-8 inline-flex items-center gap-2 rounded-2xl bg-green-500 px-7 py-3.5 font-semibold text-white shadow"
        >
          💬 Contact local support
        </a>
      </template>

      <!-- Error ───────────────────────────────────────────────── -->
      <template v-else-if="state === 'error'">
        <div class="inline-grid size-20 place-items-center rounded-3xl bg-yellow-100 text-5xl shadow-sm" aria-hidden="true">
          ⚠️
        </div>
        <h1 class="mt-6 text-2xl font-semibold text-sts-ink">
          Something went wrong
        </h1>
        <p class="mt-3 text-sm text-sts-muted">{{ errorMsg }}</p>

        <a
          :href="whatsappHelpUrl"
          target="_blank"
          rel="noopener"
          class="mt-8 inline-flex items-center gap-2 rounded-2xl bg-green-500 px-7 py-3.5 font-semibold text-white shadow"
        >
          💬 Contact us on WhatsApp
        </a>
      </template>

    </main>

  </div>
</template>
