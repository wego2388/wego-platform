<script setup lang="ts">
import { computed } from "vue";
import { useInfoCopy } from "../composables/useInfoCopy";
import { whatsappUrl } from "../content/locales";

const info = useInfoCopy();
const copy = computed(() => info.value.faq);
const contact = computed(() => info.value.contact);
const items = computed(() => copy.value.items.map((item, i) => ({ value: `faq-${i}`, ...item })));

useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm`, description: () => copy.value.intro });
useHead(() => ({
  script: [
    {
      type: "application/ld+json",
      innerHTML: JSON.stringify({
        "@context": "https://schema.org",
        "@type": "FAQPage",
        mainEntity: copy.value.items.map((item) => ({ "@type": "Question", name: item.title, acceptedAnswer: { "@type": "Answer", text: item.body } })),
      }).replace(/</g, "\\u003c"),
    },
  ],
}));
</script>

<template>
  <main id="main-content" tabindex="-1">
    <div class="bg-sts-ocean px-4 pt-12 pb-10 text-white sm:px-6 lg:px-10">
      <div class="mx-auto max-w-3xl">
        <h1 class="font-display text-3xl font-semibold sm:text-4xl">{{ copy.title }}</h1>
        <p class="mt-4 text-lg text-white/85">{{ copy.intro }}</p>
      </div>
    </div>
    <div class="mx-auto max-w-3xl px-4 py-12 sm:px-6">
      <UiAccordion :items="items" />
      <div class="mt-10 rounded-[var(--sts-radius-card)] bg-sts-sand-soft p-6 text-center">
        <p class="font-semibold">{{ copy.more }}</p>
        <UiButton class="mt-4" :href="whatsappUrl" icon="lucide:message-circle">{{ contact.whatsappCta }}</UiButton>
      </div>
    </div>
  </main>
</template>
