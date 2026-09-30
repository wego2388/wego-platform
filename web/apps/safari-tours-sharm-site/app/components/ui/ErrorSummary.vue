<script setup lang="ts">
/** Focusable list of form errors; each item links to its field. */
defineProps<{ title: string; errors: { fieldId: string; message: string }[] }>();
</script>

<template>
  <div
    v-if="errors.length"
    role="alert"
    tabindex="-1"
    class="rounded-[var(--sts-radius-card)] border border-sts-danger/40 bg-sts-danger-soft p-4 text-sm text-sts-danger"
  >
    <p class="font-semibold">{{ title }}</p>
    <ul class="mt-2 list-disc space-y-1 ps-5">
      <li v-for="error in errors" :key="error.fieldId">
        <a :href="`#${error.fieldId}`" class="underline underline-offset-2">{{ error.message }}</a>
      </li>
    </ul>
  </div>
</template>
