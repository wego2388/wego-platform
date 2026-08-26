<script setup lang="ts">
import { computed, ref } from "vue";
import { directionFor, type SdcLocale, siteCopy, whatsappUrl } from "../content/locales";

const locale = ref<SdcLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "تواصل · Sharm Divers Club" : "Contact · Sharm Divers Club",
  htmlAttrs: {
    dir: direction.value,
    lang: locale.value,
  },
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas px-6 py-8 text-sdc-ink lg:px-10">
    <header class="mx-auto flex max-w-6xl items-center justify-between">
      <NuxtLink to="/" class="font-semibold text-sdc-deep-bright">{{ direction === "rtl" ? "→" : "←" }} Sharm Divers Club</NuxtLink>
      <button type="button" class="rounded-full border border-sdc-border bg-white px-4 py-2 text-sm font-semibold" @click="toggleLocale">
        {{ copy.languageName }}
      </button>
    </header>

    <section class="mx-auto mt-14 max-w-3xl text-center">
      <h1 class="font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.contact.heading }}</h1>
      <p class="mx-auto mt-5 max-w-2xl leading-8 text-sdc-muted">{{ copy.contact.body }}</p>
    </section>

    <section class="mx-auto mt-12 mb-16 grid max-w-4xl gap-5 sm:grid-cols-2">
      <a :href="whatsappUrl" target="_blank" rel="noopener" class="rounded-[1.75rem] border border-sdc-border bg-sdc-deep p-6 text-white shadow-sm sm:col-span-2">
        <p class="text-xs font-bold tracking-[0.12em] text-sdc-sand uppercase">{{ copy.contact.whatsappLabel }}</p>
        <p class="mt-2 reference text-lg font-semibold" dir="ltr">+20 10 6646 1010</p>
        <p class="mt-2 text-sm text-white/80">{{ copy.contact.whatsappBody }}</p>
      </a>
      <div class="rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
        <p class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.contact.phoneLabel }}</p>
        <p class="reference mt-2 text-lg font-semibold" dir="ltr">+20 10 6646 1010</p>
      </div>
      <div class="rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
        <p class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.contact.emailLabel }}</p>
        <p class="reference mt-2 text-lg font-semibold" dir="ltr">Sales@sharmdiversclub.com</p>
      </div>
      <div class="rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
        <p class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.contact.locationLabel }}</p>
        <p class="mt-2 font-semibold">Royal Grand Sharm Hotel, Hadabet Um Sid, Sharm El Sheikh, Egypt</p>
      </div>
      <div class="rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
        <p class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.contact.hoursLabel }}</p>
        <p class="reference mt-2 font-semibold" dir="ltr">Daily 08:00–20:00</p>
      </div>
    </section>
  </main>
</template>
