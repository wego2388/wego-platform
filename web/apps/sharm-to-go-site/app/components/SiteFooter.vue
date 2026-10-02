<script setup lang="ts">
import { computed } from "vue";
import { contact, emailLink, whatsappLink } from "../content/contact";
import { siteCopy, type SharmLocale } from "../content/locales";

const props = defineProps<{ locale: SharmLocale }>();
const copy = computed(() => siteCopy[props.locale]);
</script>

<template>
  <footer class="border-t border-black/5 bg-sharm-surface px-6 py-12 text-sm text-sharm-muted lg:px-10">
    <div class="mx-auto grid max-w-7xl gap-10 sm:grid-cols-2 lg:grid-cols-4">
      <div>
        <NuxtLink to="/" class="flex items-center gap-3 text-sharm-ink">
          <img src="/icon-192.png" alt="" width="44" height="44" class="size-11" aria-hidden="true">
          <span class="font-display text-lg font-semibold">Sharm To Go</span>
        </NuxtLink>
        <p class="mt-4 max-w-sm leading-6">{{ copy.footerFull.tagline }}</p>
      </div>
      <div>
        <h2 class="font-bold text-sharm-ink">{{ copy.footerFull.exploreHeading }}</h2>
        <ul class="mt-4 space-y-3">
          <li><NuxtLink to="/experiences" class="hover:text-sharm-sea">{{ copy.footerFull.exploreLinks.experiences }}</NuxtLink></li>
          <li><NuxtLink to="/track" class="hover:text-sharm-sea">{{ copy.footerFull.exploreLinks.trackRequest }}</NuxtLink></li>
        </ul>
      </div>
      <div>
        <h2 class="font-bold text-sharm-ink">{{ copy.footerFull.companyHeading }}</h2>
        <ul class="mt-4 space-y-3">
          <li><NuxtLink to="/about" class="hover:text-sharm-sea">{{ copy.nav.about }}</NuxtLink></li>
          <li><NuxtLink to="/faq" class="hover:text-sharm-sea">{{ copy.footerFull.companyLinks.faq }}</NuxtLink></li>
          <li><NuxtLink to="/contact" class="hover:text-sharm-sea">{{ copy.footerFull.companyLinks.contact }}</NuxtLink></li>
          <li><NuxtLink to="/privacy" class="hover:text-sharm-sea">{{ copy.footerFull.legal.privacy }}</NuxtLink></li>
          <li><NuxtLink to="/terms" class="hover:text-sharm-sea">{{ copy.footerFull.legal.terms }}</NuxtLink></li>
        </ul>
      </div>
      <div>
        <h2 class="font-bold text-sharm-ink">{{ copy.footerFull.contactHeading }}</h2>
        <p class="money mt-4 space-y-3">
          <a :href="whatsappLink(locale === 'ar' ? 'مرحبًا Sharm To Go' : 'Hello Sharm To Go')" target="_blank" rel="noopener" class="block font-semibold text-sharm-sea hover:underline">WhatsApp {{ contact.whatsappDisplay }}</a>
          <a :href="emailLink('Sharm To Go')" class="block font-semibold text-sharm-sea hover:underline">{{ contact.email }}</a>
        </p>
      </div>
    </div>
    <div class="mx-auto mt-10 flex max-w-7xl flex-wrap justify-between gap-3 border-t border-sharm-border pt-6">
      <p>{{ copy.footerFull.rights }}</p><p class="font-semibold text-sharm-sea">{{ copy.footer }}</p>
    </div>
  </footer>
</template>
