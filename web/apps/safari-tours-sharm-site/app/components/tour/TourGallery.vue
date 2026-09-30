<script setup lang="ts">
import { computed, ref } from "vue";
import type { PublicTourContent } from "@wego/api-contract";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { tourPageCopy } from "../../content/tourPage";

/** Approved photos: a scroll-snap strip and a keyboard-friendly lightbox. */
const props = defineProps<{ media: PublicTourContent["media"] }>();
const locale = useSiteLocale();
const copy = computed(() => tourPageCopy[locale.value].gallery);
const open = ref(false);
const index = ref(0);
const current = computed(() => props.media[index.value]);

function show(i: number) {
  index.value = i;
  open.value = true;
}
function step(delta: number) {
  index.value = (index.value + delta + props.media.length) % props.media.length;
}
// Arrow keys work wherever focus is inside the open lightbox (it starts on Close).
useEventListener("keydown", (event: KeyboardEvent) => {
  if (open.value) onKey(event);
});
function onKey(event: KeyboardEvent) {
  const rtl = document.documentElement.dir === "rtl";
  if (event.key === "ArrowRight") step(rtl ? -1 : 1);
  if (event.key === "ArrowLeft") step(rtl ? 1 : -1);
}
</script>

<template>
  <div>
    <ul class="flex snap-x snap-mandatory gap-3 overflow-x-auto pb-2">
      <li v-for="(item, i) in media" :key="item.path" class="w-64 shrink-0 snap-start sm:w-72">
        <button type="button" class="block w-full overflow-hidden rounded-[var(--sts-radius-media)]" :aria-label="copy.open(i + 1)" @click="show(i)">
          <NuxtImg :src="item.path" :alt="item.alt" :width="item.width" :height="item.height" sizes="288px" loading="lazy" class="aspect-[4/3] w-full object-cover transition-transform hover:scale-[1.03]" />
        </button>
      </li>
    </ul>
    <UiDialog v-model:open="open" :title="copy.heading" :close-label="copy.close">
      <div v-if="current" class="grid gap-3">
        <NuxtImg :src="current.path" :alt="current.alt" :width="current.width" :height="current.height" sizes="(max-width: 768px) 100vw, 900px" class="max-h-[70vh] w-full rounded-[var(--sts-radius-media)] object-contain" />
        <div class="flex items-center justify-between gap-3">
          <UiButton variant="secondary" size="sm" icon="lucide:chevron-left" class="rtl:[&_svg]:-scale-x-100" :aria-label="copy.previous" @click="step(-1)" />
          <p class="text-sm text-sts-muted" aria-live="polite">{{ copy.counter(index + 1, media.length) }}</p>
          <UiButton variant="secondary" size="sm" icon="lucide:chevron-right" class="rtl:[&_svg]:-scale-x-100" :aria-label="copy.next" @click="step(1)" />
        </div>
      </div>
    </UiDialog>
  </div>
</template>
