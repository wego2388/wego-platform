<script setup lang="ts">
import SiteSubHeader from "../components/SiteSubHeader.vue";
import SiteFooter from "../components/SiteFooter.vue";
import { contact, emailLink, whatsappLink } from "../content/contact";
import { useSiteLocale } from "../composables/useSiteLocale";

const { locale, copy, direction, toggleLocale } = useSiteLocale();
useHead(() => ({ title: locale.value === "ar" ? "تواصل معنا · Sharm To Go" : "Contact · Sharm To Go", htmlAttrs: { dir: direction.value, lang: locale.value } }));
</script>

<template>
  <main id="main-content" :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <div class="mx-auto max-w-4xl">
      <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
      <section class="mt-12 rounded-[2rem] bg-sharm-surface p-8 shadow-sm sm:p-12">
        <h1 class="font-display text-4xl font-semibold">{{ copy.contactPage.heading }}</h1>
        <p class="mt-5 max-w-2xl text-lg leading-8 text-sharm-muted">{{ copy.contactPage.body }}</p>
        <div class="mt-8 flex flex-wrap gap-3">
          <a :href="whatsappLink(locale === 'ar' ? 'مرحبًا Sharm To Go، محتاج مساعدتكم في ترتيب رحلتي.' : 'Hello Sharm To Go, I would like help planning my trip.')" target="_blank" rel="noopener" class="rounded-full bg-sharm-action px-6 py-3 font-bold text-white">{{ copy.contactPage.whatsappCta }}</a>
          <a :href="emailLink('Sharm To Go enquiry')" class="rounded-full border border-sharm-sea px-6 py-3 font-bold text-sharm-sea">{{ copy.contactPage.emailCta }}</a>
        </div>
        <p class="money mt-6 font-semibold">{{ contact.whatsappDisplay }} · {{ contact.email }}</p>
        <p class="mt-4 text-sm leading-6 text-sharm-muted">{{ copy.contactPage.responseNote }}</p>
      </section>
    </div>
    <SiteFooter :locale="locale" />
  </main>
</template>
