<script setup lang="ts">
/** Focusable list of form errors; each item links to its field. */
defineProps<{ title: string; errors: { fieldId: string; message: string }[] }>();
/** Move focus to the field, not just scroll to it. */
function focusField(event: MouseEvent, fieldId: string) {
  const field = document.getElementById(fieldId);
  if (!field) return;
  event.preventDefault();
  field.focus();
  field.scrollIntoView({ block: "center" });
}
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
        <a :href="`#${error.fieldId}`" class="underline underline-offset-2" @click="focusField($event, error.fieldId)">{{ error.message }}</a>
      </li>
    </ul>
  </div>
</template>
