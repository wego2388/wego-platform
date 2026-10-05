<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useSalesStatus } from "../../composables/useSalesStatus";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { enquiryCopy } from "../../content/enquiry";
const { data: sales, refresh } = await useSalesStatus();
const locale = useSiteLocale();
const copy = computed(() => enquiryCopy[locale.value]);
onMounted(() => { void refresh(); });
</script>

<template>
  <aside v-if="sales?.bookingMode === 'ENQUIRY_ONLY' || !sales" class="border-b border-sts-border bg-sts-sand-soft px-4 py-3 text-sm text-sts-ink" role="status" data-booking-notice>
    <p class="mx-auto max-w-7xl">{{ sales?.bookingMode === 'ENQUIRY_ONLY' ? copy.notice : copy.unknown }}</p>
  </aside>
</template>
