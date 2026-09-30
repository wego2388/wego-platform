<script setup lang="ts">
import { computed } from "vue";
import { useSiteTheme, type SiteTheme } from "../../composables/useSiteTheme";

/** Cycles system → light → dark; the icon shows the current choice. */
const theme = useSiteTheme();
const NEXT: Record<SiteTheme, SiteTheme> = { system: "light", light: "dark", dark: "system" };
const ICON: Record<SiteTheme, string> = { system: "lucide:monitor", light: "lucide:sun", dark: "lucide:moon" };
const props = defineProps<{ labels?: Partial<Record<SiteTheme, string>>; tone?: "light" | "dark" }>();
const label = computed(() => props.labels?.[theme.value] ?? `Theme: ${theme.value}`);
</script>

<template>
  <button
    type="button"
    class="grid size-10 place-items-center rounded-full"
    :class="tone === 'dark' ? 'text-sts-ink hover:bg-sts-sand-soft' : 'text-white hover:bg-white/12'"
    :aria-label="label"
    :title="label"
    @click="theme = NEXT[theme]"
  >
    <Icon :name="ICON[theme]" class="size-5" aria-hidden="true" />
  </button>
</template>
