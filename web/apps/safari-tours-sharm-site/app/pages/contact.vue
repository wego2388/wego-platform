<script setup lang="ts">
import { computed } from "vue";
import SiteHeader from "../components/SiteHeader.vue";
import SiteFooter from "../components/SiteFooter.vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl, whatsappPhone, siteEmail } from "../content/locales";
import type { StsLocale } from "../content/locales";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: copy.value.nav.contact + " — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
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

    <main id="main-content" tabindex="-1">
      <div class="bg-sts-ocean px-6 py-16 text-white lg:px-10">
        <div class="mx-auto max-w-7xl">
          <h1 class="font-display text-3xl font-semibold sm:text-4xl">
            {{ copy.nav.contact }}
          </h1>
        </div>
      </div>

      <section class="mx-auto max-w-3xl px-6 py-14 lg:px-10">
        <div class="grid gap-5 sm:grid-cols-2">
          <!-- WhatsApp — primary -->
          <a
            :href="whatsappUrl"
            target="_blank"
            rel="noopener"
            class="hover-lift flex flex-col gap-3 rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-7 shadow-sm"
          >
            <span class="text-3xl" aria-hidden="true">💬</span>
            <span class="text-lg font-semibold">WhatsApp</span>
            <span class="text-sm text-sts-muted" dir="ltr">{{ whatsappPhone }}</span>
            <span class="mt-auto inline-flex rounded-full bg-[#25D366] px-4 py-2 text-sm font-semibold text-white">
              {{ copy.whatsappFab }}
            </span>
          </a>

          <!-- Email -->
          <a
            :href="`mailto:${siteEmail}`"
            class="hover-lift flex flex-col gap-3 rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-7 shadow-sm"
          >
            <span class="text-3xl" aria-hidden="true">✉️</span>
            <span class="text-lg font-semibold">Email</span>
            <span class="text-sm text-sts-muted" dir="ltr">{{ siteEmail }}</span>
            <span class="mt-auto inline-flex rounded-full bg-sts-ocean px-4 py-2 text-sm font-semibold text-white">
              Send email
            </span>
          </a>
        </div>

        <!-- Free cancellation reminder -->
        <div class="mt-8 rounded-2xl bg-sts-success-soft p-5 text-sm leading-6 text-sts-ink">
          ✅ {{ copy.cancellationBody }}
        </div>
      </section>
    </main>

    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
