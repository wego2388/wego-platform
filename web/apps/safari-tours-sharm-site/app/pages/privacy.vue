<script setup lang="ts">
import { computed } from "vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { LEGAL_UPDATED, infoCopy } from "../content/info";
import { useConsent } from "../composables/useConsent";

const locale = useSiteLocale();
const consent = useConsent();
// When analytics is configured the cookie section says so (and how to change the choice).
const copy = computed(() => {
  const privacy = infoCopy[locale.value].privacy;
  if (!consent.configured.value) return privacy;
  return {
    ...privacy,
    sections: privacy.sections.map((section, i) =>
      i === 3 ? { ...section, body: [section.body[0]!.split(/(?<=\.)\s/)[0]!, infoCopy[locale.value].consent.analyticsCookies] } : section,
    ),
  };
});
useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm` });
</script>

<template>
  <InfoDocument :title="copy.title" :updated-label="copy.updated" :updated="LEGAL_UPDATED" :sections="copy.sections" />
</template>
