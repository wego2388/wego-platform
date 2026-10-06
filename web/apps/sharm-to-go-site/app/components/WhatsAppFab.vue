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
    class="fixed end-5 bottom-5 z-50 inline-flex min-h-12 items-center gap-2 rounded-full bg-[#075e54] px-5 py-3 font-bold text-white shadow-xl transition-transform hover:-translate-y-1 focus-visible:-translate-y-1"
  >
    <!-- #075e54 is WhatsApp's own darker brand green, not an arbitrary
         pick — the lighter #1f9d61 this replaced gave white text only a
         3.46:1 contrast ratio (axe: color-contrast, needs 4.5:1 at this
         bold 16px size). #075e54 against white is ~7.8:1, comfortably
         past AA, and still reads as WhatsApp. -->
    <span aria-hidden="true">●</span>
    <span>WhatsApp</span>
  </a>
</template>
