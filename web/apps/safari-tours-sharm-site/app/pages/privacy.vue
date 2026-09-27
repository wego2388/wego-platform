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
  title: "Privacy Policy — Safari Tours Sharm",
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
      <h1 class="font-display text-3xl font-semibold">Privacy Policy</h1>
      <div class="prose mt-8 text-sts-muted">
        <p>
          Safari Tours Sharm collects only the information necessary to process your booking:
          your name, phone number, nationality, hotel name, and optional email address.
        </p>
        <p class="mt-4">
          We do not sell your data to third parties. Your booking details are shared only with
          Safari Tours Sharm staff to coordinate your tour. Payment is processed securely by
          Paymob — we never see your card number.
        </p>
        <p class="mt-4">
          To request deletion of your data, contact us via WhatsApp or email.
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
