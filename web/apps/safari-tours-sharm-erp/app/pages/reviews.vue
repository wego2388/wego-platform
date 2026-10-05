<script setup lang="ts">
import { onMounted } from "vue";
import { readAuthSession } from "../composables/useAuthSession";
import { useErpLocale } from "../composables/useErpLocale";
const { t } = useErpLocale();
const router = useRouter();
useHead(() => ({ title: `${t("reviews.title")} · Safari Tours Sharm` }));
// Labels are not integrations or ratings. Do not synthesize review data.
const externalSources = ["Tripadvisor", "Google", "Booking.com"];
onMounted(() => {
  if (!readAuthSession()) void router.replace("/login");
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-5xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ t("reviews.title") }}</h1>
        </div>
      </header>

      <!-- Nav -->

      <!-- Honest readiness: no ratings are invented for disconnected sources. -->
      <div class="mt-8 rounded-2xl border border-sts-gold bg-sts-gold-soft px-6 py-5">
        <div class="flex items-start gap-4">
          <div>
            <h2 class="font-semibold text-sts-ocean">{{ t("reviews.notConnected") }}</h2>
            <p class="mt-1 text-sm text-sts-muted">
              {{ t("reviews.scope") }}
            </p>
          </div>
        </div>
      </div>

      <!-- Source labels only; no fake zero counts or averages. -->
      <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <article
          v-for="source in [...externalSources, t('reviews.direct')]"
          :key="source"
          class="rounded-2xl border border-sts-border bg-sts-surface p-5 shadow-sm"
        >
          <h2 class="text-sm font-medium">{{ source }}</h2>
          <p class="mt-3 text-sm text-sts-muted">{{ t("reviews.noData") }}</p>
        </article>
      </div>

      <!-- Empty state -->
      <div class="mt-6 rounded-2xl border border-sts-border bg-sts-surface px-6 py-12 text-center shadow-sm">
        <h2 class="font-semibold text-sts-ink">{{ t("reviews.empty") }}</h2>
        <p class="mt-1 text-sm text-sts-muted">
          {{ t("reviews.provenance") }}
        </p>
      </div>

    </div>
  </main>
</template>
