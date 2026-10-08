<script setup lang="ts">
import { computed } from "vue";
import { artworkSource, brandArtwork, brandArtworkCopy, type BrandArtworkKind } from "../../content/brandArtwork";
import { useSiteLocale } from "../../composables/useSiteLocale";

const props = withDefaults(defineProps<{ kind: BrandArtworkKind; priority?: boolean }>(), { priority: false });
const locale = useSiteLocale();
const image = computed(() => brandArtwork[props.kind]);
const sources = computed(() => ([480, 960, 1600] as const).map((width) => `${artworkSource(props.kind, width)} ${width}w`).join(", "));
</script>

<template>
  <figure :data-brand-art="kind" :class="priority ? 'absolute inset-0' : 'relative mb-5 aspect-[3/2] overflow-hidden rounded-xl'">
    <img
      :src="artworkSource(kind, priority ? 1600 : 960)" :srcset="sources"
      :sizes="priority ? '100vw' : '(min-width: 1280px) 220px, (min-width: 1024px) 300px, (min-width: 640px) 45vw, 90vw'"
      :width="image.width" :height="image.height" alt="" aria-hidden="true"
      :loading="priority ? 'eager' : 'lazy'" :fetchpriority="priority ? 'high' : 'auto'" decoding="async"
      class="absolute inset-0 h-full w-full object-cover" :class="priority ? 'rtl:-scale-x-100' : ''"
    >
    <figcaption class="absolute inset-x-2 bottom-2 z-20 w-fit rounded-md bg-sts-ocean/95 px-2 py-1 text-[0.65rem] leading-4 text-white" :class="priority ? 'ms-2 me-auto max-w-[18rem]' : ''">
      {{ brandArtworkCopy[locale] }}
    </figcaption>
  </figure>
</template>
