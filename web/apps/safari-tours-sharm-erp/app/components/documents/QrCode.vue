<script setup lang="ts">
import { computed } from "vue";
import { encode } from "uqr";

/**
 * QR code drawn as inline SVG paths (no raw HTML). `uqr` 0.1.3 (MIT) is the
 * only QR dependency; the caller passes the public my-booking URL, which
 * carries no customer data.
 */
const props = defineProps<{ value: string; label: string }>();
const qr = computed(() => encode(props.value, { ecc: "M", border: 3 }));
const path = computed(() => {
  const { data } = qr.value;
  let d = "";
  data.forEach((row, y) => row.forEach((dark, x) => { if (dark) d += `M${x} ${y}h1v1h-1z`; }));
  return d;
});
</script>

<template>
  <svg class="doc-qr" :viewBox="`0 0 ${qr.size} ${qr.size}`" role="img" :aria-label="label" shape-rendering="crispEdges" xmlns="http://www.w3.org/2000/svg">
    <rect :width="qr.size" :height="qr.size" fill="#fff" />
    <path :d="path" fill="#000" />
  </svg>
</template>
