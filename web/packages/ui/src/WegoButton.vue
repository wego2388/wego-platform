<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: "primary" | "secondary";
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
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    class="inline-flex items-center justify-center gap-2 rounded-wego-control px-4 py-2.5 font-semibold transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:opacity-60"
    :class="
      variant === 'primary'
        ? 'bg-wego-accent text-white'
        : 'border border-wego-border bg-wego-surface text-wego-ink'
    "
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
