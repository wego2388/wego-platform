<script setup lang="ts">
import { computed } from "vue";
import WhatsAppFab from "./components/WhatsAppFab.vue";
import { useSiteLocale } from "./composables/useSiteLocale";
import { directionFor } from "./content/locales";

const locale = useSiteLocale();
const _direction = computed(() => directionFor(locale.value));
const skipLabel = computed(() =>
  locale.value === "ar" ? "تخطي إلى المحتوى" : "Skip to content",
);
</script>

<template>
  <a href="#main-content" class="sts-skip-link">{{ skipLabel }}</a>
  <NuxtPage />
  <WhatsAppFab />
</template>
