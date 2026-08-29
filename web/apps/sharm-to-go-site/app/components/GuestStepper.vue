<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  count: number;
  decreaseLabel: string;
  increaseLabel: string;
  label: string;
  maximum?: number;
  minimum?: number;
}>();

const emit = defineEmits<{ "update:count": [value: number] }>();

const minimum = computed(() => props.minimum ?? 0);
const maximum = computed(() => props.maximum ?? 12);

function change(delta: number) {
  emit("update:count", Math.min(maximum.value, Math.max(minimum.value, props.count + delta)));
}
</script>

<template>
  <div class="flex min-h-16 items-center justify-between gap-4 rounded-2xl border border-sharm-border bg-white px-4 py-3">
    <span class="font-medium">{{ label }}</span>
    <div class="flex items-center gap-3">
      <button
        type="button"
        class="grid size-11 place-items-center rounded-full border border-sharm-border text-xl font-semibold disabled:cursor-not-allowed disabled:opacity-35"
        :aria-label="`${decreaseLabel} ${label}`"
        :disabled="count <= minimum"
        @click="change(-1)"
      >
        −
      </button>
      <output class="min-w-7 text-center text-lg font-semibold" :aria-label="label">{{ count }}</output>
      <button
        type="button"
        class="grid size-11 place-items-center rounded-full border border-sharm-border text-xl font-semibold disabled:cursor-not-allowed disabled:opacity-35"
        :aria-label="`${increaseLabel} ${label}`"
        :disabled="count >= maximum"
        @click="change(1)"
      >
        +
      </button>
    </div>
  </div>
</template>
