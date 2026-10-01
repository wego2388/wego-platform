<script setup lang="ts">
import { computed } from "vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { infoCopy } from "../content/info";

const locale = useSiteLocale();
const copy = computed(() => infoCopy[locale.value].about);
useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm`, description: () => copy.value.intro });
</script>

<template>
  <InfoDocument :title="copy.title" :intro="copy.intro" :sections="copy.sections">
    <UiButton to="/tours" size="lg" icon-end="lucide:arrow-right" class="justify-self-start">{{ copy.cta }}</UiButton>
  </InfoDocument>
</template>
