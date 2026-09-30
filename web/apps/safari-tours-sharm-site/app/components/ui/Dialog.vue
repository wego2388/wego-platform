<script setup lang="ts">
import {
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
} from "reka-ui";

/** Centered modal with focus trap, Escape to close and focus restore (Reka UI). */
const open = defineModel<boolean>("open", { default: false });
defineProps<{ title: string; description?: string; closeLabel?: string }>();
</script>

<template>
  <DialogRoot v-model:open="open">
    <slot name="trigger" />
    <DialogPortal>
      <DialogOverlay class="sts-overlay fixed inset-0 z-[var(--sts-z-modal)] bg-sts-ocean/55 backdrop-blur-sm" />
      <DialogContent
        class="sts-pop fixed start-1/2 top-1/2 z-[var(--sts-z-modal)] w-[min(92vw,32rem)] -translate-x-1/2 -translate-y-1/2 rtl:translate-x-1/2
               rounded-[var(--sts-radius-card)] bg-sts-surface p-6 text-sts-ink shadow-sts-overlay"
      >
        <DialogTitle class="text-lg font-semibold">{{ title }}</DialogTitle>
        <DialogDescription v-if="description" class="mt-1 text-sm text-sts-muted">{{ description }}</DialogDescription>
        <div class="mt-4"><slot /></div>
        <div v-if="$slots.actions" class="mt-6 flex flex-wrap justify-end gap-2"><slot name="actions" /></div>
        <DialogClose class="absolute end-3 top-3 grid size-9 place-items-center rounded-full text-sts-muted hover:bg-sts-sand-soft" :aria-label="closeLabel ?? 'Close'">
          <Icon name="lucide:x" class="size-5" aria-hidden="true" />
        </DialogClose>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
