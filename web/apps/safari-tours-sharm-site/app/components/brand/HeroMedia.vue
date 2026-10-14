<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { allowHeroVideo, type ConnectionPreference } from "../../utils/heroPlayback";

const locale = useSiteLocale();
const copy = computed(() => ({
  en: { pause: "Pause background video", play: "Play background video" },
  ar: { pause: "إيقاف فيديو الخلفية", play: "تشغيل فيديو الخلفية" },
  ru: { pause: "Приостановить фоновое видео", play: "Воспроизвести фоновое видео" },
  it: { pause: "Metti in pausa il video di sfondo", play: "Riproduci il video di sfondo" },
})[locale.value]);
const video = ref<HTMLVideoElement>();
const allowed = ref(false);
const failed = ref(false);
const playing = ref(false);
let userPaused = false;
let dispose = () => {};

function play() {
  const player = video.value;
  if (!player || document.hidden || userPaused) return;
  player.muted = true;
  void player.play().catch(() => { failed.value = true; });
}
function toggle() {
  userPaused = playing.value;
  if (userPaused) video.value?.pause();
  else play();
}
watch(video, player => { if (player) play(); }, { flush: "post" });
onMounted(() => {
  const desktop = window.matchMedia("(min-width: 768px)");
  const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const connection = (navigator as Navigator & { connection?: ConnectionPreference & EventTarget }).connection;
  let ready = false;
  const update = () => { allowed.value = ready && allowHeroVideo(desktop.matches, motion.matches, connection); };
  const visibility = () => { if (document.hidden) video.value?.pause(); else play(); };
  // Do not compete with the SSR poster, headline or initial page hydration.
  const timer = window.setTimeout(() => { ready = true; update(); }, 1800);
  desktop.addEventListener("change", update);
  motion.addEventListener("change", update);
  connection?.addEventListener("change", update);
  document.addEventListener("visibilitychange", visibility);
  dispose = () => {
    window.clearTimeout(timer);
    desktop.removeEventListener("change", update);
    motion.removeEventListener("change", update);
    connection?.removeEventListener("change", update);
    document.removeEventListener("visibilitychange", visibility);
  };
});
onBeforeUnmount(() => { dispose(); });
</script>

<template>
  <div class="pointer-events-none absolute inset-0" aria-hidden="true">
    <img src="/videos/safari-hero-poster.webp" srcset="/videos/safari-hero-poster-640.webp 640w, /videos/safari-hero-poster.webp 1280w" sizes="100vw" alt="" width="1280" height="720" fetchpriority="high" loading="eager" class="h-full w-full object-cover">
    <video v-if="allowed && !failed" ref="video" src="/videos/safari-hero-20261015.mp4" poster="/videos/safari-hero-poster.webp" muted loop playsinline preload="none" tabindex="-1" class="absolute inset-0 h-full w-full object-cover" @playing="playing = true" @pause="playing = false" @error="failed = true" />
  </div>
  <button v-if="allowed && !failed" type="button" class="absolute bottom-4 end-4 z-20 inline-flex min-h-11 items-center gap-2 rounded-full bg-sts-ocean/90 px-4 py-2 text-xs font-semibold text-white ring-1 ring-white/40 hover:bg-sts-ocean focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white" :aria-label="playing ? copy.pause : copy.play" @click="toggle">
    <Icon :name="playing ? 'lucide:pause' : 'lucide:play'" class="size-4" aria-hidden="true" />
    {{ playing ? copy.pause : copy.play }}
  </button>
</template>
