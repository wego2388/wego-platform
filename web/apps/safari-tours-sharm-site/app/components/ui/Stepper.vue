<script setup lang="ts">
import { computed } from "vue";

/** Accessible +/- counter (e.g. adults/children) that never leaves [min, max]. */
const model = defineModel<number>({ required: true });
const props = withDefaults(defineProps<{ label: string; min?: number; max?: number; hint?: string }>(), { min: 0, max: 20, hint: undefined });

const canDecrease = computed(() => model.value > props.min);
const canIncrease = computed(() => model.value < props.max);
function change(delta: number) {
  model.value = Math.min(props.max, Math.max(props.min, model.value + delta));
}
</script>

<template>
  <div class="flex items-center justify-between gap-4">
    <div>
      <p class="text-sm font-semibold text-sts-ink">{{ label }}</p>
      <p v-if="hint" class="text-xs text-sts-muted">{{ hint }}</p>
    </div>
    <div class="flex items-center gap-3" role="group" :aria-label="label">
      <button
        type="button"
        class="grid size-10 place-items-center rounded-full border border-sts-border text-sts-ink disabled:opacity-40"
        :disabled="!canDecrease"
        :aria-label="`${label} −1`"
        @click="change(-1)"
      >
        <Icon name="lucide:minus" class="size-4" aria-hidden="true" />
      </button>
      <output class="min-w-6 text-center text-lg font-semibold tabular-nums" aria-live="polite">{{ model }}</output>
      <button
        type="button"
        class="grid size-10 place-items-center rounded-full border border-sts-border text-sts-ink disabled:opacity-40"
        :disabled="!canIncrease"
        :aria-label="`${label} +1`"
        @click="change(1)"
      >
        <Icon name="lucide:plus" class="size-4" aria-hidden="true" />
      </button>
    </div>
  </div>
</template>
