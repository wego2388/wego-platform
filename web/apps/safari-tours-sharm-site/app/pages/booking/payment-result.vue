<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { bookingResultCopy } from "../../content/bookingResult";
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
const locale = useSiteLocale();
const copy = computed(() => bookingResultCopy[locale.value]);

useSeoMeta({ title: () => `${copy.value.checking.title} — Safari Tours Sharm`, robots: "noindex" });

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
    errorMsg.value = copy.value.error.missing;
    return;
  }

  // Poll server for authoritative payment status
  await pollPaymentStatus();
});

// Stop polling (and never redirect) once the visitor has left this page.
let disposed = false;
onBeforeUnmount(() => {
  disposed = true;
});

async function pollPaymentStatus(attempt = 0): Promise<void> {
  if (disposed) return;
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
        if (disposed) return;
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
    errorMsg.value = copy.value.error.status;
  }
}

function checkAgain() {
  state.value = "loading";
  void pollPaymentStatus();
}

const whatsappHelpUrl = computed(() => {
  const msg = encodeURIComponent(copy.value.whatsapp.paymentHelp(bookingRef.value ?? "—"));
  return `${whatsappUrl}?text=${msg}`;
});
</script>

<template>
  <main id="main-content" tabindex="-1" class="mx-auto max-w-xl px-4 py-16 text-center sm:px-6">
    <div aria-live="polite">
      <template v-if="state === 'loading' || state === 'pending'">
        <span class="mx-auto block size-14 animate-spin rounded-full border-4 border-sts-ocean-bright border-t-transparent motion-reduce:animate-none" aria-hidden="true" />
        <h1 class="mt-6 font-display text-2xl font-semibold">{{ state === 'loading' ? copy.checking.title : copy.checking.waiting }}</h1>
        <p class="mt-3 text-sm text-sts-muted">{{ copy.checking.body }}</p>
        <div v-if="state === 'pending'" class="mt-8 grid justify-items-center gap-3">
          <p class="text-sm text-sts-muted">{{ copy.checking.slow }}</p>
          <UiButton variant="secondary" icon="lucide:refresh-cw" @click="checkAgain">{{ copy.checking.again }}</UiButton>
          <UiButton :href="whatsappHelpUrl" icon="lucide:message-circle">{{ copy.support }}</UiButton>
        </div>
      </template>

      <template v-else-if="state === 'confirmed'">
        <span class="mx-auto grid size-16 place-items-center rounded-full bg-sts-success-soft text-sts-success" aria-hidden="true">
          <Icon name="lucide:circle-check" class="size-9" />
        </span>
        <h1 class="mt-6 font-display text-3xl font-semibold">{{ copy.paid.title }}</h1>
        <p class="mt-3 text-sts-muted">{{ copy.paid.body }}</p>
      </template>

      <template v-else-if="state === 'failed'">
        <span class="mx-auto grid size-16 place-items-center rounded-full bg-sts-danger-soft text-sts-danger" aria-hidden="true">
          <Icon name="lucide:circle-x" class="size-9" />
        </span>
        <h1 class="mt-6 font-display text-3xl font-semibold">{{ copy.failed.title }}</h1>
        <p class="mt-3 text-sts-muted">{{ copy.failed.body }}</p>
        <div class="mt-8 flex flex-wrap justify-center gap-3">
          <UiButton to="/tours" variant="secondary">{{ copy.failed.retry }}</UiButton>
          <UiButton :href="whatsappHelpUrl" icon="lucide:message-circle">{{ copy.support }}</UiButton>
        </div>
      </template>

      <template v-else-if="state === 'review'">
        <span class="mx-auto grid size-16 place-items-center rounded-full bg-sts-warning-soft text-sts-warning" aria-hidden="true">
          <Icon name="lucide:shield-alert" class="size-9" />
        </span>
        <h1 class="mt-6 font-display text-3xl font-semibold">{{ copy.review.title }}</h1>
        <p class="mt-3 text-sts-muted">{{ copy.review.body }}</p>
        <UiButton class="mt-8" :href="whatsappHelpUrl" icon="lucide:message-circle">{{ copy.support }}</UiButton>
      </template>

      <template v-else>
        <span class="mx-auto grid size-16 place-items-center rounded-full bg-sts-warning-soft text-sts-warning" aria-hidden="true">
          <Icon name="lucide:triangle-alert" class="size-9" />
        </span>
        <h1 class="mt-6 font-display text-2xl font-semibold">{{ copy.error.title }}</h1>
        <p class="mt-3 text-sm text-sts-muted">{{ errorMsg }}</p>
        <div class="mt-8 flex flex-wrap justify-center gap-3">
          <UiButton to="/my-booking" variant="secondary">{{ copy.unconfirmed.check }}</UiButton>
          <UiButton :href="whatsappHelpUrl" icon="lucide:message-circle">{{ copy.support }}</UiButton>
        </div>
      </template>
    </div>
  </main>
</template>
