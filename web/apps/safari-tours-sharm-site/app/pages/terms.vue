<script setup lang="ts">
import { computed } from "vue";
import SiteHeader from "../components/SiteHeader.vue";
import SiteFooter from "../components/SiteFooter.vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy } from "../content/locales";
import type { StsLocale } from "../content/locales";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];
const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: "Terms & Conditions — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "robots", content: "noindex" }],
}));
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
    <main id="main-content" tabindex="-1" class="mx-auto max-w-3xl px-6 py-16 lg:px-10">
      <h1 class="font-display text-3xl font-semibold">Terms &amp; Conditions</h1>
      <div class="prose mt-8 text-sts-muted">
        <p>
          By booking a tour with Safari Tours Sharm, you agree to the following terms.
          Prices are in EUR and are fixed at booking time.
        </p>
        <p class="mt-4">
          <strong>Cancellation policy:</strong> Free cancellation up to 24 hours before your
          tour start time. Cancellations within 24 hours may incur a small fee.
          No-shows are non-refundable. Full refund for cancellations due to bad weather
          or boat unavailability.
        </p>
        <p class="mt-4">
          <strong>Hotel pickup:</strong> Included in the tour price. Please ensure your
          hotel name and room number are correct at booking time. We will contact you
          on WhatsApp to confirm the pickup time.
        </p>
        <p class="mt-4">
          Safari Tours Sharm reserves the right to modify tour itineraries due to
          weather, safety concerns or force majeure events.
        </p>
      </div>
    </main>
    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
