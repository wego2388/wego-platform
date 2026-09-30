<script setup lang="ts">
import { CheckboxIndicator, CheckboxRoot } from "reka-ui";

const model = defineModel<boolean>({ default: false });
defineProps<{ id?: string; invalid?: boolean }>();
</script>

<template>
  <label class="inline-flex cursor-pointer items-start gap-3 text-sm text-sts-ink">
    <CheckboxRoot
      :id="id"
      v-model="model"
      :aria-invalid="invalid || undefined"
      class="mt-0.5 grid size-5 shrink-0 place-items-center rounded-md border border-sts-border bg-sts-surface
             data-[state=checked]:border-sts-ocean data-[state=checked]:bg-sts-ocean aria-invalid:border-sts-danger"
    >
      <CheckboxIndicator>
        <Icon name="lucide:check" class="size-3.5 text-white" aria-hidden="true" />
      </CheckboxIndicator>
    </CheckboxRoot>
    <span><slot /></span>
  </label>
</template>
