<script setup lang="ts">
import { computed, ref } from "vue";
import { directionFor, type SdcLocale, whatsappUrl } from "../content/locales";
import { offeringPreviewCode, offeringPreviewCopy, offeringPreviewPrice } from "../content/offering-preview";

const locale = ref<SdcLocale>("en");
const direction = computed(() => directionFor(locale.value));
const copy = computed(() => offeringPreviewCopy[locale.value]);

useHead(() => ({
  title: locale.value === "ar" ? "تفاصيل الخدمة · Sharm Divers Club" : "Offering detail · Sharm Divers Club",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "robots", content: "noindex,nofollow" }],
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}

const askUrl = computed(() => {
  const text = locale.value === "ar"
    ? `مرحبًا، عايز أسأل عن: ${copy.value.name} (${offeringPreviewCode})`
    : `Hi, I'd like to ask about: ${copy.value.name} (${offeringPreviewCode})`;
  return `${whatsappUrl}?text=${encodeURIComponent(text)}`;
});
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas text-sdc-ink">
    <div class="border-b border-sdc-border bg-white">
      <header class="mx-auto flex max-w-5xl items-center justify-between gap-4 px-5 py-5 lg:px-10">
        <NuxtLink to="/discover" class="flex items-center gap-3 font-semibold text-sdc-deep-bright">
          <span aria-hidden="true">{{ direction === "rtl" ? "→" : "←" }}</span> {{ copy.back }}
        </NuxtLink>
        <button type="button" class="min-h-11 rounded-full border border-sdc-border px-4 text-sm font-semibold" @click="toggleLocale">
          {{ copy.switchLanguage }}
        </button>
      </header>
    </div>

    <div class="border-b border-sdc-border bg-sdc-turquoise-soft px-5 py-3 text-center text-sm font-semibold text-sdc-deep" role="status">
      {{ copy.notice }}
    </div>

    <section class="mx-auto max-w-5xl px-5 py-10 lg:px-10">
      <span class="inline-flex rounded-full bg-sdc-sand-soft px-4 py-2 text-xs font-bold tracking-[0.1em] text-sdc-deep uppercase">
        {{ copy.eyebrow }}
      </span>

      <div class="mt-8 grid gap-8 lg:grid-cols-[1fr_20rem]">
        <div class="rounded-[1.75rem] border border-sdc-border bg-white p-6 sm:p-8">
          <p class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.category }}</p>
          <h1 class="mt-2 font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.name }}</h1>
          <p class="mt-3 leading-7 text-sdc-muted">{{ copy.audience }}</p>

          <div class="mt-6 flex flex-wrap gap-2 text-xs font-semibold text-sdc-deep-bright">
            <span class="rounded-full bg-sdc-canvas px-3 py-1">{{ copy.diveCount }}</span>
          </div>

          <div class="mt-8 border-t border-sdc-border pt-6">
            <h2 class="text-lg font-semibold">{{ copy.priceLabel }}</h2>
            <p class="money mt-3 text-3xl font-semibold text-sdc-deep">{{ offeringPreviewPrice.amount }} {{ offeringPreviewPrice.currency }}</p>
            <p class="mt-2 text-xs font-semibold text-sdc-deep-bright">{{ copy.priceNote }}</p>
          </div>
        </div>

        <aside class="h-fit rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
          <p class="text-sm leading-6 text-sdc-muted">{{ copy.sourceNote }}</p>
          <a :href="askUrl" target="_blank" rel="noopener" class="mt-5 flex min-h-11 items-center justify-center rounded-full bg-sdc-deep px-5 text-sm font-semibold text-white">
            {{ copy.whatsapp }}
          </a>
        </aside>
      </div>
    </section>
  </main>
</template>
