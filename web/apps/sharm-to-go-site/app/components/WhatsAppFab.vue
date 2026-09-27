<script setup lang="ts">
import { whatsappLink } from "../content/contact";

const href = whatsappLink("Hello Sharm To Go, I would like help planning my Sharm trip.");
</script>

<template>
  <a
    :href="href"
    target="_blank"
    rel="noopener"
    aria-label="Ask Sharm To Go on WhatsApp"
    class="fixed end-5 bottom-5 z-50 inline-flex min-h-12 items-center gap-2 rounded-full bg-[#1f9d61] px-5 py-3 font-bold text-white shadow-xl transition-transform hover:-translate-y-1 focus-visible:-translate-y-1"
  >
    <span aria-hidden="true">●</span>
    <span>WhatsApp</span>
  </a>
</template>
