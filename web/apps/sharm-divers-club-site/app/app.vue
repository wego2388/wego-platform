<script setup lang="ts">
import { computed } from "vue";
import WhatsAppFab from "./components/WhatsAppFab.vue";
import { useSiteLocale } from "./composables/useSiteLocale";

const locale = useSiteLocale();
const skipLabel = computed(() => (locale.value === "ar" ? "تخطي إلى المحتوى" : "Skip to content"));
</script>

<template>
  <a href="#main-content" class="sdc-skip-link">{{ skipLabel }}</a>
  <NuxtPage />
  <WhatsAppFab />
</template>
