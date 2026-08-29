<script setup lang="ts">
import { tripadvisorUrl, whatsappUrl } from "../content/locales";
import type { SdcLocale, siteCopy } from "../content/locales";

defineProps<{ locale: SdcLocale; copy: (typeof siteCopy)["en"]["footer"] }>();
</script>

<template>
  <footer class="border-t border-sdc-border bg-sdc-surface px-5 py-14 lg:px-10">
    <div class="mx-auto grid max-w-7xl gap-10 sm:grid-cols-2 lg:grid-cols-4">
      <div>
        <div class="flex items-center gap-3 font-semibold text-sdc-ink">
          <span class="grid size-9 place-items-center rounded-xl bg-sdc-sand font-display text-sm font-black text-sdc-ink">SDC</span>
          <span>Sharm Divers Club</span>
        </div>
        <p class="mt-4 max-w-xs text-sm leading-6 text-sdc-muted">{{ copy.tagline }}</p>
        <a :href="tripadvisorUrl" target="_blank" rel="noopener" class="mt-4 inline-flex text-sm font-semibold text-sdc-deep-bright hover:underline">
          {{ copy.tripadvisor }}
        </a>
      </div>
      <div>
        <h2 class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.exploreHeading }}</h2>
        <ul class="mt-4 grid gap-2 text-sm">
          <li><NuxtLink to="/discover" class="text-sdc-muted hover:text-sdc-ink">{{ copy.exploreLinks.discover }}</NuxtLink></li>
          <li><NuxtLink to="/design-system" class="text-sdc-muted hover:text-sdc-ink">{{ copy.exploreLinks.designSystem }}</NuxtLink></li>
        </ul>
      </div>
      <div>
        <h2 class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.companyHeading }}</h2>
        <ul class="mt-4 grid gap-2 text-sm">
          <li><NuxtLink to="/about" class="text-sdc-muted hover:text-sdc-ink">{{ copy.companyLinks.about }}</NuxtLink></li>
          <li><NuxtLink to="/faq" class="text-sdc-muted hover:text-sdc-ink">{{ copy.companyLinks.faq }}</NuxtLink></li>
          <li><NuxtLink to="/contact" class="text-sdc-muted hover:text-sdc-ink">{{ copy.companyLinks.contact }}</NuxtLink></li>
        </ul>
      </div>
      <div>
        <h2 class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.contactHeading }}</h2>
        <ul class="mt-4 grid gap-2 text-sm">
          <li><a :href="whatsappUrl" target="_blank" rel="noopener" class="text-sdc-muted hover:text-sdc-ink">WhatsApp</a></li>
          <li><a href="mailto:Sales@sharmdiversclub.com" class="reference text-sdc-muted hover:text-sdc-ink" dir="ltr">Sales@sharmdiversclub.com</a></li>
          <li class="reference text-sdc-muted" dir="ltr">+20 10 6646 1010</li>
        </ul>
      </div>
    </div>
    <div class="mx-auto mt-10 flex max-w-7xl flex-col items-center gap-3 border-t border-sdc-border pt-6 text-center text-sm text-sdc-muted sm:flex-row sm:justify-between">
      <span>{{ copy.rights }}</span>
      <span class="flex gap-4">
        <NuxtLink to="/privacy" class="hover:text-sdc-ink">{{ copy.legal.privacy }}</NuxtLink>
        <NuxtLink to="/terms" class="hover:text-sdc-ink">{{ copy.legal.terms }}</NuxtLink>
      </span>
    </div>
  </footer>
</template>
