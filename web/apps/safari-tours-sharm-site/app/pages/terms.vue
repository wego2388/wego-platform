<script setup lang="ts">
import { computed } from "vue";
import { LEGAL_UPDATED } from "../content/info";
import { termsDescription } from "../utils/seoDescriptions";
import { useInfoCopy } from "../composables/useInfoCopy";

const info = useInfoCopy();
const copy = computed(() => info.value.terms);
useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm`, description: () => termsDescription(copy.value) });
</script>

<template>
  <InfoDocument :title="copy.title" :updated-label="copy.updated" :updated="LEGAL_UPDATED" :sections="copy.sections" />
</template>
