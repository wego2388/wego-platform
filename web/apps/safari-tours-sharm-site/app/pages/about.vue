<script setup lang="ts">
import { computed } from "vue";
import { useInfoCopy } from "../composables/useInfoCopy";

const info = useInfoCopy();
const copy = computed(() => info.value.about);
useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm`, description: () => copy.value.intro });
</script>

<template>
  <InfoDocument :title="copy.title" :intro="copy.intro" :sections="copy.sections">
    <UiButton to="/tours" size="lg" icon-end="lucide:arrow-right" class="justify-self-start">{{ copy.cta }}</UiButton>
  </InfoDocument>
</template>
