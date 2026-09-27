<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import SiteHeader from "../../components/SiteHeader.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "../../content/locales";
import type { StsLocale } from "../../content/locales";
import {
  lookupBooking,
  formatPrice,
  formatDate,
  formatTimeSlot,
  readStoredBookingConfirmation,
  type BookingConfirmation,
} from "../../composables/usePublicToursApi";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];
const route = useRoute();

const locale    = useSiteLocale();
const copy      = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: "Booking Confirmed — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
}));

const booking  = ref<BookingConfirmation | null>(null);
const state    = ref<"loading" | "loaded" | "error">("loading");
const errorMsg = ref("");

onMounted(async () => {
  const ref   = route.query.ref   as string | undefined;
  const phone = route.query.phone as string | undefined;

  if (!ref) {
    // Confirmation reached right after booking — no phone needed yet
    // We only have the reference; show a generic success without full details
    state.value = "loaded";
    return;
  }

  const stored = readStoredBookingConfirmation(ref);
  if (stored) {
    booking.value = stored;
    state.value = "loaded";
    return;
  }

  if (ref && phone) {
    try {
      booking.value = await lookupBooking(ref, phone);
      state.value = "loaded";
    } catch {
      state.value = "error";
      errorMsg.value = "Could not load booking details.";
    }
  } else {
    // ref without phone — partial display
    state.value = "loaded";
  }
});

const reference = computed(
  () => booking.value?.reference ?? (route.query.ref as string | undefined) ?? "",
);

const whatsappConfirmUrl = computed(() => {
  const msg = encodeURIComponent(
    `Hi, I just made a booking with reference: ${reference.value}. Please confirm my pickup details.`,
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

      <template v-else-if="state === 'loaded'">
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
            <dd class="font-bold text-sts-ocean">{{ formatPrice(booking.totalPrice) }}</dd>
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
            <li>📱 We will contact you on WhatsApp before your trip with pickup time and details.</li>
            <li>🏨 Our driver will meet you at your hotel entrance.</li>
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

      <div v-else-if="state === 'error'" class="text-red-600">
        {{ errorMsg }}
      </div>

    </main>

    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
