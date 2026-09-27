<script setup lang="ts">
import { whatsappUrl, siteEmail, whatsappPhone } from "../content/locales";

defineProps<{
  tagline: string;
  links: { tours: string; contact: string; privacy: string; terms: string };
  rights: string;
}>();
</script>

<template>
  <footer class="border-t border-sts-border bg-sts-ocean text-white">
    <div class="mx-auto max-w-7xl px-6 py-14 lg:px-10">
      <div class="grid gap-10 sm:grid-cols-2 lg:grid-cols-4">
        <!-- Brand -->
        <div>
          <div class="flex items-center gap-3 font-semibold">
            <span
              class="grid size-9 place-items-center rounded-xl bg-sts-sand font-display text-sm font-black text-sts-ocean"
            >STS</span>
            <span class="text-sm font-semibold">Safari Tours Sharm</span>
          </div>
          <p class="mt-4 max-w-xs text-sm leading-6 text-white/70">{{ tagline }}</p>
        </div>

        <!-- Quick links -->
        <div>
          <h2 class="text-xs font-bold tracking-[0.12em] text-sts-sand uppercase">Tours</h2>
          <ul class="mt-4 grid gap-2 text-sm">
            <li>
              <NuxtLink to="/tours" class="text-white/70 hover:text-white">{{ links.tours }}</NuxtLink>
            </li>
            <li>
              <NuxtLink to="/category/desert" class="text-white/70 hover:text-white">Desert & Safari</NuxtLink>
            </li>
            <li>
              <NuxtLink to="/category/sea" class="text-white/70 hover:text-white">Sea & Water</NuxtLink>
            </li>
            <li>
              <NuxtLink to="/category/cultural" class="text-white/70 hover:text-white">Cultural Tours</NuxtLink>
            </li>
          </ul>
        </div>

        <!-- Company -->
        <div>
          <h2 class="text-xs font-bold tracking-[0.12em] text-sts-sand uppercase">Company</h2>
          <ul class="mt-4 grid gap-2 text-sm">
            <li>
              <NuxtLink to="/contact" class="text-white/70 hover:text-white">{{ links.contact }}</NuxtLink>
            </li>
            <li>
              <NuxtLink to="/privacy" class="text-white/70 hover:text-white">{{ links.privacy }}</NuxtLink>
            </li>
            <li>
              <NuxtLink to="/terms" class="text-white/70 hover:text-white">{{ links.terms }}</NuxtLink>
            </li>
          </ul>
        </div>

        <!-- Contact -->
        <div>
          <h2 class="text-xs font-bold tracking-[0.12em] text-sts-sand uppercase">Contact</h2>
          <ul class="mt-4 grid gap-2 text-sm">
            <li>
              <a
                :href="whatsappUrl"
                target="_blank"
                rel="noopener"
                class="text-white/70 hover:text-white"
              >WhatsApp</a>
            </li>
            <li>
              <a
                :href="`tel:${whatsappPhone}`"
                class="text-white/70 hover:text-white"
                dir="ltr"
              >{{ whatsappPhone }}</a>
            </li>
            <li>
              <a
                :href="`mailto:${siteEmail}`"
                class="text-white/70 hover:text-white"
                dir="ltr"
              >{{ siteEmail }}</a>
            </li>
          </ul>
        </div>
      </div>

      <!-- Bottom bar -->
      <div
        class="mt-10 flex flex-col items-center justify-between gap-3 border-t border-white/15 pt-6 text-center text-sm text-white/50 sm:flex-row"
      >
        <span>{{ rights }}</span>
        <span class="flex gap-4">
          <NuxtLink to="/privacy" class="hover:text-white">{{ links.privacy }}</NuxtLink>
          <NuxtLink to="/terms" class="hover:text-white">{{ links.terms }}</NuxtLink>
        </span>
      </div>
    </div>
  </footer>
</template>
