<script setup lang="ts">
import { computed } from "vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { LEGAL_UPDATED, infoCopy } from "../content/info";

const locale = useSiteLocale();
const copy = computed(() => infoCopy[locale.value].privacy);
useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm` });
</script>

<template>
  <InfoDocument :title="copy.title" :updated-label="copy.updated" :updated="LEGAL_UPDATED" :sections="copy.sections" />
</template>
