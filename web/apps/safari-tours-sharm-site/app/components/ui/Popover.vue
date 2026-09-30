<script setup lang="ts">
import { PopoverArrow, PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from "reka-ui";
const open = defineModel<boolean>("open", { default: false });
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child><slot name="trigger" /></PopoverTrigger>
    <PopoverPortal>
      <PopoverContent
        :side-offset="8"
        class="sts-pop z-[var(--sts-z-dropdown)] rounded-[var(--sts-radius-control)] border border-sts-border bg-sts-surface-raised p-3 text-sm text-sts-ink shadow-sts-raised"
      >
        <slot />
        <PopoverArrow class="fill-sts-surface-raised" />
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
