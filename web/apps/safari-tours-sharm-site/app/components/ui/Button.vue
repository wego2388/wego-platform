<script setup lang="ts">
import { computed } from "vue";

/**
 * The one button. `to` renders a locale-aware link, `href` an external link,
 * otherwise a <button>. Sunset (primary) is reserved for conversion actions.
 */
const props = withDefaults(
  defineProps<{
    variant?: "primary" | "secondary" | "ghost" | "inverse";
    size?: "sm" | "md" | "lg";
    to?: string;
    href?: string;
    type?: "button" | "submit";
    loading?: boolean;
    disabled?: boolean;
    block?: boolean;
    icon?: string;
    iconEnd?: string;
  }>(),
  { variant: "primary", size: "md", to: undefined, href: undefined, type: "button", loading: false, disabled: false, block: false, icon: undefined, iconEnd: undefined },
);

const classes = computed(() => [
  "inline-flex items-center justify-center gap-2 font-semibold select-none",
  "transition-[transform,background-color,box-shadow,color] duration-[var(--sts-dur-quick)] ease-[var(--sts-ease)]",
  "active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-55 aria-disabled:cursor-not-allowed aria-disabled:opacity-55",
  "rounded-[var(--sts-radius-btn)]",
  {
    sm: "min-h-9 px-3.5 text-sm",
    md: "min-h-11 px-5 text-[0.95rem]",
    lg: "min-h-13 px-7 text-base",
  }[props.size],
  {
    primary: "bg-sts-sunset text-sts-ocean hover:bg-sts-sunset-strong shadow-sts-base",
    secondary: "border border-sts-border bg-sts-surface text-sts-ink hover:border-sts-ocean-bright",
    ghost: "text-sts-ocean-bright hover:bg-sts-sand-soft",
    inverse: "bg-white/12 text-white ring-1 ring-white/30 hover:bg-white/20 backdrop-blur",
  }[props.variant],
  props.block ? "w-full" : "",
]);
const inert = computed(() => props.disabled || props.loading);
</script>

<template>
  <NuxtLinkLocale v-if="to && !inert" :to="to" :class="classes">
    <Icon v-if="icon" :name="icon" class="size-[1.15em] shrink-0" aria-hidden="true" />
    <slot />
    <Icon v-if="iconEnd" :name="iconEnd" class="size-[1.15em] shrink-0 rtl:-scale-x-100" aria-hidden="true" />
  </NuxtLinkLocale>
  <a v-else-if="href && !inert" :href="href" :class="classes" target="_blank" rel="noopener noreferrer">
    <Icon v-if="icon" :name="icon" class="size-[1.15em] shrink-0" aria-hidden="true" />
    <slot />
    <Icon v-if="iconEnd" :name="iconEnd" class="size-[1.15em] shrink-0 rtl:-scale-x-100" aria-hidden="true" />
  </a>
  <button v-else :type="type" :class="classes" :disabled="inert" :aria-busy="loading || undefined">
    <Icon v-if="loading" name="lucide:loader-circle" class="size-[1.15em] shrink-0 animate-spin" aria-hidden="true" />
    <Icon v-else-if="icon" :name="icon" class="size-[1.15em] shrink-0" aria-hidden="true" />
    <slot />
    <Icon v-if="iconEnd && !loading" :name="iconEnd" class="size-[1.15em] shrink-0 rtl:-scale-x-100" aria-hidden="true" />
  </button>
</template>
