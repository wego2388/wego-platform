<script setup lang="ts">
import { computed } from "vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { googleReviewUrl, tripadvisorReviewUrl } from "../content/locales";
import { reviewRequestCopy } from "../content/reviewRequest";

const locale = useSiteLocale();
const copy = computed(() => reviewRequestCopy[locale.value]);
</script>

<template>
  <section data-review-request aria-labelledby="review-request-heading" class="overflow-hidden rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-sand-soft shadow-sts-card">
    <div class="grid gap-6 p-6 sm:p-8 md:grid-cols-[minmax(0,1fr)_auto] md:items-center">
      <div>
        <p class="text-xs font-bold tracking-[0.12em] text-sts-ocean-bright uppercase">{{ copy.eyebrow }}</p>
        <h2 id="review-request-heading" class="mt-2 font-display text-2xl font-semibold text-sts-ocean">{{ copy.title }}</h2>
        <p class="mt-2 max-w-xl text-sts-muted">{{ copy.body }}</p>
        <p class="mt-3 max-w-xl text-sm text-sts-muted">{{ copy.note }}</p>
      </div>
      <div class="grid gap-3 sm:grid-cols-2 md:grid-cols-1">
        <UiButton :href="googleReviewUrl" variant="secondary" icon="lucide:external-link" data-google-review-link>{{ copy.google }}</UiButton>
        <UiButton :href="tripadvisorReviewUrl" variant="secondary" icon="lucide:external-link" data-tripadvisor-review-link>{{ copy.tripadvisor }}</UiButton>
      </div>
    </div>
  </section>
</template>
