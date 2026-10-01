<script setup lang="ts">
import { computed } from "vue";
import { useConsent } from "../../composables/useConsent";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { infoCopy } from "../../content/info";

/** Asked once, only when the deployment has analytics configured. */
const consent = useConsent();
const locale = useSiteLocale();
const copy = computed(() => infoCopy[locale.value].consent);
const mounted = useMounted();
</script>

<template>
  <section
    v-if="mounted && consent.needsAnswer.value"
    class="fixed inset-x-3 bottom-3 z-[var(--sts-z-toast)] mx-auto max-w-2xl rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-5 shadow-sts-overlay sm:inset-x-6"
    role="region"
    aria-labelledby="consent-title"
    aria-describedby="consent-body"
  >
    <h2 id="consent-title" class="font-semibold">{{ copy.title }}</h2>
    <p id="consent-body" class="mt-1 text-sm text-sts-muted">
      {{ copy.body }}
      <NuxtLinkLocale to="/privacy" class="font-semibold text-sts-ocean-bright underline">{{ copy.more }}</NuxtLinkLocale>
    </p>
    <div class="mt-4 flex flex-wrap justify-end gap-2">
      <UiButton variant="secondary" size="sm" @click="consent.set('denied')">{{ copy.decline }}</UiButton>
      <UiButton size="sm" @click="consent.set('granted')">{{ copy.accept }}</UiButton>
    </div>
  </section>
</template>
