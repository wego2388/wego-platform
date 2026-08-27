<script setup lang="ts">
import { computed } from "vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { siteCopy, whatsappUrl } from "../content/locales";

const locale = useSiteLocale();
const label = computed(() => siteCopy[locale.value].whatsappFab);
</script>

<template>
  <a
    :href="whatsappUrl"
    target="_blank"
    rel="noopener"
    class="wa-fab fixed bottom-5 end-5 z-50 flex items-center gap-2 rounded-full bg-sdc-turquoise px-5 py-3 font-semibold text-white shadow-xl transition-transform hover:-translate-y-1"
    :aria-label="label"
  >
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" aria-hidden="true">
      <path
        d="M12 3a9 9 0 0 0-7.75 13.55L3 21l4.6-1.2A9 9 0 1 0 12 3Z"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linejoin="round"
      />
      <path
        d="M8.7 8.4c.2-.5.5-.5.7-.5h.5c.2 0 .4 0 .6.4.2.5.7 1.6.7 1.7.1.1.1.3 0 .4-.1.2-.2.3-.3.4l-.4.4c-.1.1-.3.3-.1.6.2.3.8 1.2 1.7 2 1.1 1 2 1.3 2.4 1.4.3.1.5.1.7-.1l.6-.7c.2-.3.4-.2.6-.1l1.5.7c.2.1.4.2.4.4.1.5-.1 1.2-.5 1.6-.5.5-1.4.9-2.4.6-1.6-.4-3.2-1.4-4.5-2.6-1.2-1.2-2.2-2.7-2.6-4.2-.3-1 .1-1.9.6-2.4Z"
        fill="currentColor"
      />
    </svg>
    <span class="hidden sm:inline">{{ label }}</span>
  </a>
</template>
