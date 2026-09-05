<script setup lang="ts">
import { ref, useId, watchEffect } from "vue";

// Built on the native <dialog> element deliberately, not a hand-rolled
// focus trap: showModal()/close() give real, browser-implemented focus
// containment, Escape-to-close (the native `cancel` event), and focus
// restoration to whatever was focused before opening — the exact
// behavior WEGO-014's plan calls for, without reimplementing what every
// browser vendor already gets right. This is what replaces the app's
// existing window.confirm/window.prompt call sites.
const props = defineProps<{
  open: boolean;
  title: string;
}>();

const emit = defineEmits<{
  close: [];
}>();

const dialogRef = ref<HTMLDialogElement | null>(null);
const titleId = useId();

// flush: "post" (not the default "pre", and not a plain `{ immediate:
// true }` watch) deliberately — this must run *after* the component's
// own DOM exists, since it reads dialogRef.value. A plain immediate
// watch fires during setup, before the <dialog> element is mounted, so
// an initial `open: true` prop would silently never call showModal() —
// a real bug this exact test setup caught before it shipped.
watchEffect(
  () => {
    const dialog = dialogRef.value;
    if (!dialog) return;
    if (props.open && !dialog.open) dialog.showModal();
    else if (!props.open && dialog.open) dialog.close();
  },
  { flush: "post" },
);

function handleClose(): void {
  // One handler for both the native `cancel` event (Escape) and `close`
  // (any other dismissal, including a caller-driven `open` -> false) —
  // a single source of truth so the caller's own `open` state can never
  // drift from what the browser actually did.
  emit("close");
}
</script>

<template>
  <dialog
    ref="dialogRef"
    :aria-labelledby="titleId"
    class="m-auto w-full max-w-md rounded-wego-card border border-wego-border bg-wego-surface p-6 text-wego-ink backdrop:bg-wego-surface-overlay"
    @close="handleClose"
    @cancel="handleClose"
  >
    <h2 :id="titleId" class="text-lg font-semibold">{{ title }}</h2>
    <div class="mt-4">
      <slot />
    </div>
    <div v-if="$slots.actions" class="mt-6 flex justify-end gap-3">
      <slot name="actions" />
    </div>
  </dialog>
</template>
