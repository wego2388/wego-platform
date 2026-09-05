<script setup lang="ts">
// Status pills (ACTIVE/CLOSED/DRAFT/POSTED/PENDING/...) are rendered as
// plain text across every ERP page today — no shared component, no
// consistent tone mapping. This is the real one, built on the tokens
// WEGO-014 Phase 2 added. Never relies on color alone: pair with text
// that says the actual status, matching every existing usage.
withDefaults(
  defineProps<{
    tone?: "neutral" | "accent" | "success" | "warning" | "danger" | "info";
  }>(),
  {
    tone: "neutral",
  },
);

const toneClass: Record<string, string> = {
  neutral: "bg-wego-surface-sunken text-wego-muted",
  accent: "bg-wego-accent-soft text-wego-accent",
  success: "bg-wego-success-soft text-wego-success",
  warning: "bg-wego-warning-soft text-wego-warning",
  danger: "bg-wego-danger-soft text-wego-danger",
  info: "bg-wego-info-soft text-wego-info",
};
</script>

<template>
  <span
    class="inline-flex items-center rounded-wego-pill px-3 py-1 text-xs font-semibold tracking-wide uppercase"
    :class="toneClass[tone]"
  >
    <slot />
  </span>
</template>
