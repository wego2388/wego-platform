<script setup lang="ts">
import { computed } from "vue";
import type { NuxtError } from "#app";
import WhatsAppFab from "./components/WhatsAppFab.vue";
import { useSiteLocale } from "./composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "./content/locales";

defineProps<{ error: NuxtError }>();

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

const text = computed(() => locale.value === "ar"
  ? { title: "الصفحة مش موجودة", body: "الرابط ده مش موجود عندنا. جرّب الصفحة الرئيسية أو راسلنا على واتساب.", home: "الصفحة الرئيسية" }
  : { title: "Page not found", body: "This page doesn't exist. Try the homepage, or message us on WhatsApp.", home: "Go to homepage" });

function goHome() {
  clearError({ redirect: "/" });
}
</script>

<template>
  <main :dir="direction" :lang="locale" class="grid min-h-screen place-items-center bg-sdc-canvas px-6 text-center text-sdc-ink">
    <div>
      <span class="grid size-16 mx-auto place-items-center rounded-2xl bg-sdc-sand font-display text-2xl font-black text-sdc-deep">SDC</span>
      <h1 class="mt-6 font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ text.title }}</h1>
      <p class="mx-auto mt-4 max-w-md leading-7 text-sdc-muted">{{ text.body }}</p>
      <div class="mt-8 flex flex-wrap justify-center gap-3">
        <button type="button" class="rounded-full bg-sdc-deep px-6 py-3 font-semibold text-white" @click="goHome">{{ text.home }}</button>
        <a :href="whatsappUrl" target="_blank" rel="noopener" class="rounded-full border border-sdc-border bg-sdc-surface px-6 py-3 font-semibold text-sdc-deep-bright">
          {{ copy.whatsappFab }}
        </a>
      </div>
    </div>
    <WhatsAppFab />
  </main>
</template>
