<script setup lang="ts">
withDefaults(
  defineProps<{
    variant: "success" | "warning" | "danger";
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
};
</script>

<template>
  <p :role="role" class="text-sm font-medium" :class="variantClass[variant]"><slot /></p>
</template>
