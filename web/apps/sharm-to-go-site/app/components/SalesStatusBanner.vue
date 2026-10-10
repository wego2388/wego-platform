<script setup lang="ts">
import { useSalesStatus, salesCopy } from "../composables/useSalesStatus";
import { useSiteLocale } from "../composables/useSiteLocale";
import { whatsappLink } from "../content/contact";

const { requestsOpen } = useSalesStatus(true);
const { locale, direction } = useSiteLocale();
</script>

<template>
  <aside v-if="requestsOpen === false" role="status" :dir="direction" :lang="locale" class="border-b border-sharm-border bg-sharm-lagoon px-6 py-3 text-center text-sm text-sharm-action">
    {{ salesCopy[locale].paused }}
    <a :href="whatsappLink(salesCopy[locale].contact)" target="_blank" rel="noopener" class="ms-2 font-semibold underline">{{ salesCopy[locale].contact }}</a>
  </aside>
</template>
