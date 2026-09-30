<script setup lang="ts">
withDefaults(
  defineProps<{ tone?: "neutral" | "success" | "warning" | "danger" | "brand" | "sea" | "desert" | "cultural" | "shows" | "transfers"; icon?: string }>(),
  { tone: "neutral", icon: undefined },
);
const TONES = {
  neutral: "bg-sts-sand-soft text-sts-ink",
  success: "bg-sts-success-soft text-sts-success",
  warning: "bg-sts-warning-soft text-sts-warning",
  danger: "bg-sts-danger-soft text-sts-danger",
  brand: "bg-sts-ocean text-white",
  sea: "bg-sts-cat-sea/12 text-sts-cat-sea",
  desert: "bg-sts-cat-desert/12 text-sts-cat-desert",
  cultural: "bg-sts-cat-cultural/12 text-sts-cat-cultural",
  shows: "bg-sts-cat-shows/12 text-sts-cat-shows",
  transfers: "bg-sts-cat-transfers/12 text-sts-cat-transfers",
} as const;
</script>

<template>
  <span class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold" :class="TONES[tone]">
    <Icon v-if="icon" :name="icon" class="size-3.5" aria-hidden="true" /><slot />
  </span>
</template>
