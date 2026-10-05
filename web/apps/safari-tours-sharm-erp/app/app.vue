<script setup lang="ts">
import { useErpLocale } from "./composables/useErpLocale";
import { isLocalizedErpRoute } from "./utils/erpLocale";

const { locale, direction } = useErpLocale();
const route = useRoute();
useHead(() => ({ htmlAttrs: { lang: locale.value, dir: direction.value } }));
</script>

<template>
  <NuxtLayout>
    <div :lang="isLocalizedErpRoute(route.path) ? locale : 'en'" :dir="isLocalizedErpRoute(route.path) ? direction : 'ltr'">
      <NuxtPage />
    </div>
  </NuxtLayout>
</template>
