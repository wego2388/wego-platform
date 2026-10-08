<script setup lang="ts">
import { computed } from "vue";
import { LEGAL_UPDATED } from "../content/info";
import { useInfoCopy } from "../composables/useInfoCopy";
import { useConsent } from "../composables/useConsent";

const consent = useConsent();
const info = useInfoCopy();
// When analytics is configured the cookie section says so (and how to change the choice).
const copy = computed(() => {
  const privacy = info.value.privacy;
  if (!consent.configured.value) return privacy;
  return {
    ...privacy,
    sections: privacy.sections.map((section, i) =>
      i === 3 ? { ...section, body: [section.body[0]!.split(/(?<=\.)\s/)[0]!, info.value.consent.analyticsCookies] } : section,
    ),
  };
});
useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm` });
</script>

<template>
  <InfoDocument :title="copy.title" :updated-label="copy.updated" :updated="LEGAL_UPDATED" :sections="copy.sections" />
</template>
