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

/** Bottom sheet on mobile, side panel from the reading-start edge on desktop. */
const open = defineModel<boolean>("open", { default: false });
withDefaults(defineProps<{ title: string; description?: string; closeLabel?: string; side?: "bottom" | "end" }>(), { side: "bottom", description: undefined, closeLabel: undefined });
</script>

<template>
  <DialogRoot v-model:open="open">
    <slot name="trigger" />
    <DialogPortal>
      <DialogOverlay class="sts-overlay fixed inset-0 z-[var(--sts-z-sheet)] bg-sts-ocean/45" />
      <DialogContent
        class="fixed z-[var(--sts-z-sheet)] flex flex-col bg-sts-surface text-sts-ink shadow-sts-overlay"
        :class="side === 'bottom'
          ? 'sts-sheet-up inset-x-0 bottom-0 max-h-[88vh] rounded-t-[var(--sts-radius-media)] pb-[env(safe-area-inset-bottom)]'
          : 'sts-sheet-side inset-y-0 end-0 w-[min(92vw,26rem)]'"
      >
        <div v-if="side === 'bottom'" class="mx-auto mt-2 h-1.5 w-12 rounded-full bg-sts-border" aria-hidden="true" />
        <header class="flex items-start justify-between gap-4 px-5 pt-4">
          <div>
            <DialogTitle class="text-lg font-semibold">{{ title }}</DialogTitle>
            <DialogDescription v-if="description" class="text-sm text-sts-muted">{{ description }}</DialogDescription>
          </div>
          <DialogClose class="grid size-9 place-items-center rounded-full text-sts-muted hover:bg-sts-sand-soft" :aria-label="closeLabel ?? 'Close'">
            <Icon name="lucide:x" class="size-5" aria-hidden="true" />
          </DialogClose>
        </header>
        <div class="flex-1 overflow-y-auto px-5 py-4"><slot /></div>
        <footer v-if="$slots.footer" class="border-t border-sts-border px-5 py-3"><slot name="footer" /></footer>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
