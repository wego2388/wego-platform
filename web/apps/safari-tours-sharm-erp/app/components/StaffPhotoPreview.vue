<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { fetchPrivatePhoto, managedPhotoAssetId } from "../composables/useTourMediaApi";
const props = defineProps<{ path: string; assetId?: string | null; token: string; alt: string; width: number; height: number; failureLabel: string }>();
const emit = defineEmits<{ ready: [boolean] }>();
const source = ref<string | null>(null);
const failed = ref(false);
const managed = computed(() => props.assetId ?? managedPhotoAssetId(props.path));
let version = 0;
let disposed = false;
let ownedUrl: string | null = null;
function release() { if (ownedUrl) URL.revokeObjectURL(ownedUrl); ownedUrl = null; source.value = null; }
watch(() => [props.path, props.token, props.assetId, props.width], async () => {
  const current = ++version;
  release(); failed.value = false; emit("ready", false);
  if (!managed.value) {
    // Legacy repo-owned paths are not bearer URLs. Never turn an arbitrary path into an image request.
    source.value = /^\/media\/tours\/[a-z0-9-]+\/[a-z0-9._-]+\.(avif|webp|jpg|jpeg|png)$/.test(props.path) ? props.path : null;
    failed.value = source.value === null;
    return;
  }
  try {
    // Derivatives exist only below the original width; small uploads need the base variant.
    const blob = await fetchPrivatePhoto(props.token, managed.value, props.width > 768 ? "w768" : "base");
    if (disposed || current !== version) return;
    ownedUrl = URL.createObjectURL(blob);
    source.value = ownedUrl;
  } catch {
    if (!disposed && current === version) failed.value = true;
  }
}, { immediate: true });
onBeforeUnmount(() => { disposed = true; version++; release(); });
</script>

<template>
  <div :aria-busy="!source && !failed" class="grid aspect-[4/3] place-items-center overflow-hidden rounded-xl bg-sts-canvas">
    <p v-if="failed" role="status" class="p-4 text-center text-sm text-sts-muted">{{ failureLabel }}</p>
    <img v-else-if="source" :src="source" :alt="alt" :width="width" :height="height" class="h-full w-full object-contain" loading="lazy" @load="emit('ready', true)" @error="failed = true; emit('ready', false)">
    <span v-else class="animate-pulse text-sm text-sts-muted motion-reduce:animate-none" aria-hidden="true">…</span>
  </div>
</template>
