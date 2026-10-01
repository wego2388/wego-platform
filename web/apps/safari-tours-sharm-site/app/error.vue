<script setup lang="ts">
import { computed } from "vue";
import type { NuxtError } from "#app";
import { useSiteLocale } from "./composables/useSiteLocale";
import { useSiteTheme } from "./composables/useSiteTheme";
import { infoCopy } from "./content/info";
import { whatsappUrl } from "./content/locales";

const props = defineProps<{ error: NuxtError }>();
const locale = useSiteLocale();
const copy = computed(() => infoCopy[locale.value].error);
const notFound = computed(() => props.error?.statusCode === 404);
const localeHead = useLocaleHead({ dir: true, lang: true });
// app.vue is not rendered for errors, so the theme attribute is set here too.
const theme = useSiteTheme();
useHead(() => ({
  title: `${notFound.value ? copy.value.notFound : copy.value.generic} — Safari Tours Sharm`,
  htmlAttrs: {
    lang: localeHead.value.htmlAttrs?.lang,
    dir: localeHead.value.htmlAttrs?.dir as "ltr" | "rtl" | undefined,
    "data-theme": theme.value === "system" ? undefined : theme.value,
  },
  meta: [{ name: "robots", content: "noindex" }],
}));
</script>

<template>
  <a href="#main-content" class="sts-skip-link">{{ copy.home }}</a>
  <NuxtLayout>
    <main id="main-content" tabindex="-1" class="mx-auto flex min-h-[60vh] max-w-xl flex-col items-center justify-center gap-5 px-4 py-20 text-center">
      <span class="grid size-16 place-items-center rounded-full bg-sts-sand-soft text-sts-ocean-bright" aria-hidden="true">
        <Icon :name="notFound ? 'lucide:map' : 'lucide:triangle-alert'" class="size-8" />
      </span>
      <p v-if="error?.statusCode" class="font-mono text-sm text-sts-muted">{{ error.statusCode }}</p>
      <h1 class="font-display text-3xl font-semibold">{{ notFound ? copy.notFound : copy.generic }}</h1>
      <p class="text-sts-muted">{{ notFound ? copy.notFoundBody : copy.genericBody }}</p>
      <div class="flex flex-wrap justify-center gap-3">
        <UiButton to="/" variant="secondary">{{ copy.home }}</UiButton>
        <UiButton to="/tours">{{ copy.tours }}</UiButton>
        <UiButton :href="whatsappUrl" variant="ghost" icon="lucide:message-circle">WhatsApp</UiButton>
      </div>
    </main>
  </NuxtLayout>
</template>
