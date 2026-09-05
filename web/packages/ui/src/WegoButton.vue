<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: "primary" | "secondary" | "destructive" | "ghost";
    loading?: boolean;
    disabled?: boolean;
    type?: "button" | "submit" | "reset";
  }>(),
  {
    variant: "primary",
    loading: false,
    disabled: false,
    type: "button",
  },
);

const variantClass: Record<string, string> = {
  primary: "bg-wego-accent text-white",
  secondary: "border border-wego-border bg-wego-surface text-wego-ink",
  destructive: "bg-wego-danger text-white",
  ghost: "text-wego-ink hover:bg-wego-surface-hover",
};
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading ? 'true' : undefined"
    class="inline-flex items-center justify-center gap-2 rounded-wego-control px-4 py-2.5 font-semibold transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:opacity-60"
    :class="variantClass[variant]"
  >
    <svg
      v-if="loading"
      class="h-4 w-4 animate-spin"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
      <path
        class="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4Z"
      />
    </svg>
    <slot />
  </button>
</template>
