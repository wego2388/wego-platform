<script setup lang="ts">
withDefaults(
  defineProps<{
    variant: "success" | "warning" | "danger" | "info";
    role?: "alert" | "status";
  }>(),
  {
    role: "alert",
  },
);

const variantClass: Record<string, string> = {
  success: "text-wego-success",
  warning: "text-wego-warning",
  danger: "text-wego-danger",
  info: "text-wego-info",
};
</script>

<template>
  <p :role="role" aria-live="polite" class="text-sm font-medium" :class="variantClass[variant]"><slot /></p>
</template>
