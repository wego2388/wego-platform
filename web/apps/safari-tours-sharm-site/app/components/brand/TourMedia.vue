<script setup lang="ts">
import { computed } from "vue";
import { CATEGORY_VISUAL, type CategoryKey } from "../../utils/categoryVisual";

/**
 * A tour picture. With an approved `src` it renders an optimised responsive
 * image; without one it renders a branded placeholder in the category colour
 * (a mockup, never a stock photo), at the same aspect ratio so nothing shifts
 * when the real photo arrives.
 */
const props = withDefaults(
  defineProps<{
    src?: string | null;
    alt: string;
    category: CategoryKey;
    ratio?: string;
    sizes?: string;
    priority?: boolean;
    label?: string;
  }>(),
  { src: null, label: undefined, ratio: "4 / 3", sizes: "(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw", priority: false },
);
const visual = computed(() => CATEGORY_VISUAL[props.category]);
</script>

<template>
  <div class="relative overflow-hidden rounded-[var(--sts-radius-media)] bg-sts-sand-soft" :style="{ aspectRatio: ratio }">
    <NuxtImg
      v-if="src"
      :src="src"
      :alt="alt"
      :sizes="sizes"
      :loading="priority ? 'eager' : 'lazy'"
      :fetchpriority="priority ? 'high' : 'auto'"
      class="absolute inset-0 size-full object-cover"
    />
    <div
      v-else
      role="img"
      :aria-label="alt"
      class="absolute inset-0 grid place-items-center"
      :style="{ background: `radial-gradient(circle at 70% 25%, rgb(255 140 66 / 0.35), transparent 45%), linear-gradient(150deg, var(${visual.colorVar}), var(--sts-color-ocean))` }"
    >
      <Icon :name="visual.icon" class="size-14 text-white/85" aria-hidden="true" />
      <span v-if="label" class="absolute inset-x-4 bottom-3 truncate text-center text-sm font-semibold text-white/90">{{ label }}</span>
    </div>
  </div>
</template>
