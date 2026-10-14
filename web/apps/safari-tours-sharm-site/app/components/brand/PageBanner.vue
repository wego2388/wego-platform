<script setup lang="ts">
import { computed } from "vue";
import { artworkSource, brandArtwork, brandArtworkCopy, type BrandArtworkKind } from "../../content/brandArtwork";
import { useSiteLocale } from "../../composables/useSiteLocale";
import type { PublicCategoryCover } from "@wego/api-contract";
import { curatedCover } from "../../utils/curatedMedia";
import { illustrativePhotoLabel, isIllustrativeTourPhoto } from "../../utils/tourPhotoDisclosure";
const props = withDefaults(defineProps<{ kind?: BrandArtworkKind; photo?: PublicCategoryCover }>(), { kind: "hero", photo: undefined });
const locale = useSiteLocale();
const photo = computed(() => props.photo ?? curatedCover({ hero: "ras-mohamed-white-island-boat", sea: "tiran-island-boat-trip", desert: "sunset-quad-bike" }[props.kind], locale.value));
const image = computed(() => brandArtwork[props.kind]);
const sources = computed(() => ([480, 960, 1600] as const).map(width => `${artworkSource(props.kind, width)} ${width}w`).join(", "));
</script>

<template>
  <header class="sts-page-banner relative isolate overflow-hidden bg-sts-ocean px-4 pt-12 pb-8 text-white sm:px-6 lg:px-10 lg:pt-16" :data-page-art="kind">
    <BrandSafeTourImage v-if="photo" :src="photo.path" :alt="''" :width="photo.width" :height="photo.height" :sizes="'100vw'" loading="eager" aria-hidden="true" class="pointer-events-none absolute inset-0 -z-20 h-full w-full object-cover" />
    <img v-else :src="artworkSource(kind, 960)" :srcset="sources" sizes="100vw" :width="image.width" :height="image.height" alt="" aria-hidden="true" loading="eager" decoding="async" fetchpriority="auto" class="pointer-events-none absolute inset-0 -z-20 h-full w-full object-cover">
    <BrandSectionBackdrop tone="night" />
    <div class="relative z-10"><slot /></div>
    <p v-if="isIllustrativeTourPhoto(photo?.alt)" class="relative z-10 mx-auto mt-4 max-w-7xl text-xs text-white/85">{{ illustrativePhotoLabel[locale] }}</p>
    <p v-if="!photo" class="relative z-10 mx-auto mt-8 w-fit max-w-full rounded-full border border-white/20 bg-[#082440] px-3 py-1 text-center text-[0.65rem] leading-4 text-white/85">{{ brandArtworkCopy[locale] }}</p>
  </header>
</template>

<style scoped>
.sts-page-banner::before { content: ""; position: absolute; inset: 0; z-index: -10; background: linear-gradient(100deg, rgb(8 36 64 / .94), rgb(8 36 64 / .78) 55%, rgb(8 36 64 / .4)); }
:global([dir="rtl"]) .sts-page-banner::before { background: linear-gradient(-100deg, rgb(8 36 64 / .94), rgb(8 36 64 / .78) 55%, rgb(8 36 64 / .4)); }
</style>
