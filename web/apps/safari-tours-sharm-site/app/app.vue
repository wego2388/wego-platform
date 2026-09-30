<script setup lang="ts">
import { computed } from "vue";
import WhatsAppFab from "./components/WhatsAppFab.vue";
import { useSiteLocale } from "./composables/useSiteLocale";
import { useSiteTheme } from "./composables/useSiteTheme";

const locale = useSiteLocale();
const theme = useSiteTheme();

// lang, dir, canonical and hreflang alternates are rendered on the server,
// so the first byte of every page already has the right language metadata.
const localeHead = useLocaleHead({ dir: true, lang: true, seo: true });
useHead(() => ({
  htmlAttrs: {
    lang: localeHead.value.htmlAttrs?.lang,
    dir: localeHead.value.htmlAttrs?.dir as "ltr" | "rtl" | undefined,
    "data-theme": theme.value === "system" ? undefined : theme.value,
  },
  link: localeHead.value.link,
  meta: localeHead.value.meta,
}));

const SKIP: Record<string, string> = {
  en: "Skip to content",
  ar: "تخطي إلى المحتوى",
  ru: "Перейти к содержанию",
  it: "Vai al contenuto",
};
const skipLabel = computed(() => SKIP[locale.value] ?? SKIP.en);
</script>

<template>
  <a href="#main-content" class="sts-skip-link">{{ skipLabel }}</a>
  <NuxtPage />
  <WhatsAppFab />
</template>
