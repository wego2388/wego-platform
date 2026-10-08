<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { PublicTourContent } from "@wego/api-contract";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { tourPageCopy } from "../../content/tourPage";
import { galleryStep, gallerySwipe } from "../../utils/galleryNavigation";

/** Approved photos: a scroll-snap strip and a keyboard-friendly lightbox. */
const props = defineProps<{ media: PublicTourContent["media"] }>();
const locale = useSiteLocale();
const copy = computed(() => tourPageCopy[locale.value].gallery);
const open = ref(false);
const index = ref(0);
const current = computed(() => props.media[index.value]);
let touchStart: { x: number; y: number } | null = null;
watch(() => props.media.length, count => { index.value = Math.min(index.value, Math.max(0, count - 1)); if (!count) open.value = false; });

function show(i: number) {
  if (!props.media[i]) return;
  index.value = i;
  open.value = true;
}
function step(delta: number) {
  index.value = galleryStep(index.value, delta, props.media.length);
}
function startSwipe(event: TouchEvent) { const touch = event.changedTouches[0]; touchStart = touch ? { x: touch.clientX, y: touch.clientY } : null; }
function endSwipe(event: TouchEvent) {
  const touch = event.changedTouches[0]; const start = touchStart; touchStart = null;
  if (!touch || !start || !open.value) return;
  const delta = gallerySwipe(touch.clientX - start.x, touch.clientY - start.y, document.documentElement.dir === "rtl");
  if (delta) step(delta);
}
// Arrow keys work wherever focus is inside the open lightbox (it starts on Close).
useEventListener("keydown", (event: KeyboardEvent) => {
  if (open.value) onKey(event);
});
function onKey(event: KeyboardEvent) {
  if (!props.media.length || !["ArrowRight", "ArrowLeft"].includes(event.key)) return;
  event.preventDefault();
  const rtl = document.documentElement.dir === "rtl";
  if (event.key === "ArrowRight") step(rtl ? -1 : 1);
  if (event.key === "ArrowLeft") step(rtl ? 1 : -1);
}
</script>

<template>
  <div>
    <ul class="flex snap-x snap-mandatory gap-3 overflow-x-auto pb-2">
      <li v-for="(item, i) in media" :key="item.path" class="w-64 shrink-0 snap-start sm:w-72">
        <button type="button" class="relative block w-full overflow-hidden rounded-[var(--sts-radius-media)] focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-sts-ocean" :aria-label="copy.open(i + 1)" @click="show(i)">
          <BrandSafeTourImage :src="item.path" :alt="item.alt" :width="item.width" :height="item.height" sizes="288px" loading="lazy" class="aspect-[4/3] w-full object-cover transition-transform hover:scale-[1.03]" />
          <span class="absolute bottom-2 end-2 rounded-full bg-sts-ocean/95 px-2 py-1 text-xs text-white" aria-hidden="true">{{ copy.counter(i + 1, media.length) }}</span>
        </button>
      </li>
    </ul>
    <UiDialog v-model:open="open" :title="copy.heading" :close-label="copy.close">
      <div v-if="current" class="grid gap-3">
        <div class="touch-pan-y" @touchstart.passive="startSwipe" @touchend.passive="endSwipe" @touchcancel="touchStart = null">
          <BrandSafeTourImage :key="current.path" :src="current.path" :alt="current.alt" :width="current.width" :height="current.height" sizes="(max-width: 768px) 100vw, 900px" class="max-h-[65vh] w-full rounded-[var(--sts-radius-media)] object-contain" />
        </div>
        <p v-if="current.alt" class="text-center text-sm leading-6 text-sts-muted">{{ current.alt }}</p>
        <div class="flex items-center justify-between gap-3">
          <UiButton variant="secondary" size="sm" icon="lucide:chevron-left" class="rtl:[&_svg]:-scale-x-100" :aria-label="copy.previous" :disabled="media.length < 2" @click="step(-1)" />
          <p class="text-sm text-sts-muted" aria-live="polite">{{ copy.counter(index + 1, media.length) }}</p>
          <UiButton variant="secondary" size="sm" icon="lucide:chevron-right" class="rtl:[&_svg]:-scale-x-100" :aria-label="copy.next" :disabled="media.length < 2" @click="step(1)" />
        </div>
      </div>
    </UiDialog>
  </div>
</template>
