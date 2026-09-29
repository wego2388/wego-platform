<script setup lang="ts">
import { computed, ref } from "vue";
import SiteHeader from "../components/SiteHeader.vue";
import SiteFooter from "../components/SiteFooter.vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "../content/locales";
import type { StsLocale } from "../content/locales";
import {
  lookupBooking,
  formatPrice,
  formatDate,
  formatTimeSlot,
  type PublicBookingLookup,
  PublicApiError,
} from "../composables/usePublicToursApi";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];

const locale    = useSiteLocale();
const copy      = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: "My Booking — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
}));

const reference = ref("");
const phone     = ref("");
const booking   = ref<PublicBookingLookup | null>(null);
const state     = ref<"idle" | "loading" | "loaded" | "not_found" | "error">("idle");
const errorMsg  = ref("");

async function lookup() {
  if (!reference.value.trim() || !phone.value.trim()) return;
  state.value = "loading";
  errorMsg.value = "";
  booking.value = null;
  try {
    booking.value = await lookupBooking(reference.value.trim(), phone.value.trim());
    state.value = "loaded";
  } catch (err) {
    if (err instanceof PublicApiError && err.status === 404) {
      state.value = "not_found";
    } else {
      state.value = "error";
      errorMsg.value = "Could not connect. Please try again.";
    }
  }
}

const statusColor: Record<string, string> = {
  NEW:       "bg-yellow-100 text-yellow-800",
  CONFIRMED: "bg-green-100  text-green-800",
  COMPLETED: "bg-blue-100   text-blue-800",
  CANCELLED: "bg-red-100    text-red-800",
  EXPIRED:   "bg-gray-100   text-gray-600",
};
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

    <main id="main-content" tabindex="-1" class="mx-auto max-w-xl px-6 py-16 lg:px-0">

      <h1 class="font-display text-3xl font-semibold">My Booking</h1>
      <p class="mt-2 text-sts-muted">Enter your booking reference and phone number to view your booking.</p>

      <!-- Lookup form -->
      <form class="mt-8 space-y-4" @submit.prevent="lookup">
        <div>
          <label for="ref" class="block text-sm font-medium">Booking Reference</label>
          <input
            id="ref"
            v-model="reference"
            type="text"
            class="mt-1 w-full rounded-xl border border-sts-border px-4 py-2.5 text-sm font-mono uppercase focus:outline-none focus:ring-2 focus:ring-sts-ocean"
            placeholder="STR-2026-1234"
          >
        </div>
        <div>
          <label for="lookup-phone" class="block text-sm font-medium">Phone Number</label>
          <input
            id="lookup-phone"
            v-model="phone"
            type="tel"
            class="mt-1 w-full rounded-xl border border-sts-border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sts-ocean"
            placeholder="+20 1234567890"
          >
        </div>
        <button
          type="submit"
          class="w-full rounded-2xl bg-sts-ocean py-3.5 font-semibold text-white shadow transition-transform hover:-translate-y-0.5 disabled:opacity-40"
          :disabled="state === 'loading' || !reference.trim() || !phone.trim()"
        >
          <span v-if="state === 'loading'">Searching…</span>
          <span v-else>Find My Booking</span>
        </button>
      </form>

      <!-- Not found -->
      <div
        v-if="state === 'not_found'"
        class="mt-6 rounded-2xl border border-yellow-200 bg-yellow-50 px-5 py-4 text-sm text-yellow-800"
      >
        No booking found with that reference and phone number. Please check and try again.
        <a :href="whatsappUrl" target="_blank" rel="noopener" class="mt-2 block font-semibold text-green-600 hover:underline">
          💬 Contact us on WhatsApp
        </a>
      </div>

      <!-- Error -->
      <div
        v-else-if="state === 'error'"
        class="mt-6 rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-700"
      >
        {{ errorMsg }}
      </div>

      <!-- Booking result -->
      <div v-else-if="state === 'loaded' && booking" class="mt-6">
        <div class="rounded-2xl border border-sts-border bg-sts-surface overflow-hidden">
          <!-- Status header -->
          <div class="flex items-center justify-between px-5 py-4 border-b border-sts-border">
            <span class="font-mono font-bold text-sts-ink">{{ booking.reference }}</span>
            <span
              class="rounded-full px-3 py-1 text-xs font-semibold"
              :class="statusColor[booking.status] ?? 'bg-gray-100 text-gray-600'"
            >
              {{ booking.status }}
            </span>
          </div>

          <dl class="divide-y divide-sts-border text-sm">
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
              <dt class="text-sts-muted">Hotel</dt>
              <dd class="font-medium">{{ booking.hotelName }}</dd>
            </div>
            <div class="flex justify-between px-5 py-3.5">
              <dt class="font-semibold">Total</dt>
              <dd class="font-bold text-sts-ocean">{{ formatPrice(booking.totalPrice) }}</dd>
            </div>
          </dl>

          <!-- Cancelled note -->
          <div
            v-if="booking.status === 'CANCELLED'"
            class="border-t border-sts-border bg-red-50 px-5 py-3 text-sm text-red-700"
          >
            Cancelled{{ booking.cancellationReason ? `: ${booking.cancellationReason}` : "" }}
          </div>
        </div>

        <!-- WhatsApp contact -->
        <a
          :href="whatsappUrl"
          target="_blank"
          rel="noopener"
          class="mt-4 flex items-center justify-center gap-2 rounded-2xl border border-sts-border py-3.5 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
        >
          💬 Questions? Contact us on WhatsApp
        </a>
      </div>

    </main>

    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
