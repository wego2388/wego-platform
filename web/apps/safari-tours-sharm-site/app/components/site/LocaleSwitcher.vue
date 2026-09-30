<script setup lang="ts">
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuTrigger } from "reka-ui";
import { useSiteLocale } from "../../composables/useSiteLocale";
import type { StsLocale } from "../../content/locales";

/** Language menu: every entry is written in its own language; keeps the current page. */
const locale = useSiteLocale();
const LANGUAGES: { code: StsLocale; name: string; short: string }[] = [
  { code: "en", name: "English", short: "EN" },
  { code: "ar", name: "العربية", short: "ع" },
  { code: "ru", name: "Русский", short: "RU" },
  { code: "it", name: "Italiano", short: "IT" },
];
defineProps<{ label?: string; tone?: "light" | "dark" }>();
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger
      class="inline-flex min-h-10 items-center gap-1.5 rounded-full px-3 text-sm font-semibold"
      :class="tone === 'dark' ? 'text-sts-ink hover:bg-sts-sand-soft' : 'text-white hover:bg-white/12'"
      :aria-label="label ?? 'Language'"
    >
      <Icon name="lucide:globe" class="size-4" aria-hidden="true" />
      {{ LANGUAGES.find((l) => l.code === locale)?.short }}
      <Icon name="lucide:chevron-down" class="size-3.5 opacity-70" aria-hidden="true" />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        :side-offset="8"
        align="end"
        class="sts-pop z-[var(--sts-z-dropdown)] min-w-44 rounded-[var(--sts-radius-control)] border border-sts-border bg-sts-surface-raised p-1.5 shadow-sts-raised"
      >
        <DropdownMenuItem
          v-for="language in LANGUAGES"
          :key="language.code"
          :lang="language.code"
          :dir="language.code === 'ar' ? 'rtl' : 'ltr'"
          class="flex min-h-10 cursor-pointer items-center justify-between gap-3 rounded-md px-3 text-sm text-sts-ink outline-none data-[highlighted]:bg-sts-sand-soft"
          @select="locale = language.code"
        >
          {{ language.name }}
          <Icon v-if="language.code === locale" name="lucide:check" class="size-4 text-sts-palm" aria-hidden="true" />
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>
