<script setup lang="ts">
import { computed } from "vue";
import { isManagedMediaPath, managedMediaSrcset } from "../../utils/managedMedia";
import { curatedSrcset } from "../../utils/curatedMedia";

const props = withDefaults(defineProps<{
  src: string;
  alt: string;
  width?: number;
  height?: number;
  sizes?: string;
  loading?: "lazy" | "eager";
  fetchpriority?: "high" | "auto" | "low";
}>(), { width: undefined, height: undefined, sizes: undefined, loading: "lazy", fetchpriority: "auto" });
const managed = computed(() => isManagedMediaPath(props.src));
const bundled = computed(() => curatedSrcset(props.src));
const srcset = computed(() => bundled.value ?? managedMediaSrcset(props.src, props.width));
</script>

<template>
  <img v-if="managed || bundled" :src="src" :alt="alt" :width="width" :height="height" :sizes="sizes" :srcset="srcset" :loading="loading" :fetchpriority="fetchpriority" decoding="async">
  <NuxtImg v-else :src="src" :alt="alt" :width="width" :height="height" :sizes="sizes" :loading="loading" :fetchpriority="fetchpriority" />
</template>
