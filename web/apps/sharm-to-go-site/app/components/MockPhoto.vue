<script setup lang="ts">
// A gradient-and-icon illustration standing in for real photography —
// never a fabricated/AI-generated photo pretending to be a real place,
// same honesty line already drawn elsewhere in this repo (the mobile
// app's SdcMockPhoto, this site's own earlier "tokenized color/gradient
// placeholders" note). Once the owner hands over real, rights-cleared
// photography, pass `src` and this renders that image instead — the
// gradient/icon path stays as the honest fallback when `src` is absent.
import type { CategoryTone } from "../content/categoryAccents";

const props = withDefaults(
  defineProps<{
    tone: CategoryTone;
    label: string;
    src?: string;
    class?: string;
  }>(),
  { src: undefined, class: "" },
);

const gradientByTone: Record<CategoryTone, string> = {
  sea: "from-sharm-sea-bright to-sharm-sea",
  desert: "from-sharm-sun to-sharm-terracotta",
  transfers: "from-sharm-sky to-sharm-sea",
  city: "from-sharm-terracotta to-sharm-sand",
};

const iconPathByTone: Record<CategoryTone, string> = {
  // Wave
  sea: "M2 15c1.6-1.6 3.2-1.6 4.8 0s3.2 1.6 4.8 0 3.2-1.6 4.8 0 3.2 1.6 4.8 0M2 19c1.6-1.6 3.2-1.6 4.8 0s3.2 1.6 4.8 0 3.2-1.6 4.8 0 3.2 1.6 4.8 0",
  // Sun over a dune
  desert: "M12 6v2M6.5 8.5l1.4 1.4M17.5 8.5l-1.4 1.4M12 10a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM2 20c2.5-3 5-4 7-4s3.5 1.4 5 1.4 3-1.4 5-1.4 3 1.5 3 4",
  // Car
  transfers: "M4 16V12l2-4h9l3 4v4M4 16h16M7 16a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM17 16a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z",
  // City skyline
  city: "M3 20V9l4-3v14M9 20V5l4-2v17M15 20V11l6-3v12M3 20h18",
};
</script>

<template>
  <div :class="`relative overflow-hidden bg-gradient-to-br ${gradientByTone[props.tone]} ${props.class}`" role="img" :aria-label="label">
    <img v-if="props.src" :src="props.src" :alt="label" class="size-full object-cover">
    <template v-else>
      <div
        class="pointer-events-none absolute inset-0 opacity-20"
        style="background-image: radial-gradient(circle, rgb(255 255 255 / 60%) 0 2px, transparent 3px); background-size: 22px 22px"
        aria-hidden="true"
      />
      <svg viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" class="absolute inset-0 m-auto size-10 opacity-90" aria-hidden="true">
        <path :d="iconPathByTone[props.tone]" />
      </svg>
    </template>
  </div>
</template>
