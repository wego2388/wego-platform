<script setup lang="ts">
import { computed } from "vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { infoCopy } from "../content/info";
import { facebookUrl, googleMapsUrl, instagramUrl, siteEmail, tripadvisorUrl, whatsappUrl } from "../content/locales";

const locale = useSiteLocale();
const copy = computed(() => infoCopy[locale.value].contact);
useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm`, description: () => copy.value.intro });
const phoneDisplay = "+20 111 129 2690";
</script>

<template>
  <main id="main-content" tabindex="-1">
    <div class="bg-sts-ocean px-4 pt-12 pb-10 text-white sm:px-6 lg:px-10">
      <div class="mx-auto max-w-5xl">
        <h1 class="font-display text-3xl font-semibold sm:text-4xl">{{ copy.title }}</h1>
        <p class="mt-4 max-w-2xl text-lg text-white/85">{{ copy.intro }}</p>
      </div>
    </div>

    <div class="mx-auto grid max-w-5xl gap-6 px-4 py-12 sm:px-6">
      <ul class="grid gap-5 md:grid-cols-3">
        <li class="flex flex-col gap-3 rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6">
          <span class="grid size-11 place-items-center rounded-xl bg-[#25D366] text-white" aria-hidden="true"><Icon name="lucide:message-circle" class="size-5" /></span>
          <h2 class="text-lg font-semibold">{{ copy.whatsapp }}</h2>
          <p class="flex-1 text-sm text-sts-muted">{{ copy.whatsappBody }}</p>
          <bdi dir="ltr" class="font-semibold">{{ phoneDisplay }}</bdi>
          <UiButton :href="whatsappUrl" block>{{ copy.whatsappCta }}</UiButton>
        </li>
        <li class="flex flex-col gap-3 rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6">
          <span class="grid size-11 place-items-center rounded-xl bg-sts-ocean text-white" aria-hidden="true"><Icon name="lucide:phone" class="size-5" /></span>
          <h2 class="text-lg font-semibold">{{ copy.phone }}</h2>
          <p class="flex-1 text-sm text-sts-muted">{{ copy.phoneBody }}</p>
          <bdi dir="ltr" class="font-semibold">{{ phoneDisplay }}</bdi>
          <UiButton href="tel:+201111292690" variant="secondary" block>{{ copy.call }}</UiButton>
        </li>
        <li class="flex flex-col gap-3 rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6">
          <span class="grid size-11 place-items-center rounded-xl bg-sts-ocean-bright text-white" aria-hidden="true"><Icon name="lucide:mail" class="size-5" /></span>
          <h2 class="text-lg font-semibold">{{ copy.email }}</h2>
          <p class="flex-1 text-sm text-sts-muted">{{ copy.emailBody }}</p>
          <bdi dir="ltr" class="font-semibold break-all">{{ siteEmail }}</bdi>
          <UiButton :href="`mailto:${siteEmail}`" variant="secondary" block>{{ copy.emailCta }}</UiButton>
        </li>
      </ul>

      <div class="grid gap-5 md:grid-cols-2">
        <section class="rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6" aria-labelledby="office-heading">
          <h2 id="office-heading" class="flex items-center gap-2 text-lg font-semibold"><Icon name="lucide:map-pin" class="size-5 text-sts-coral" aria-hidden="true" />{{ copy.office }}</h2>
          <p class="mt-2 text-sm text-sts-muted">{{ copy.officeBody }}</p>
          <UiButton :href="googleMapsUrl" variant="ghost" size="sm" class="mt-3" icon="lucide:external-link">{{ copy.map }}</UiButton>
        </section>
        <section class="rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6" aria-labelledby="follow-heading">
          <h2 id="follow-heading" class="text-lg font-semibold">{{ copy.follow }}</h2>
          <div class="mt-3 flex flex-wrap gap-2">
            <UiButton :href="instagramUrl" variant="secondary" size="sm" icon="lucide:instagram">Instagram</UiButton>
            <UiButton :href="facebookUrl" variant="secondary" size="sm" icon="lucide:facebook">Facebook</UiButton>
          </div>
          <h3 class="mt-5 text-sm font-bold text-sts-muted">{{ copy.reviews }}</h3>
          <div class="mt-2 flex flex-wrap gap-2">
            <UiButton :href="googleMapsUrl" variant="ghost" size="sm" icon="lucide:star">{{ copy.googleReviews }}</UiButton>
            <UiButton :href="tripadvisorUrl" variant="ghost" size="sm" icon="lucide:star">{{ copy.tripadvisor }}</UiButton>
          </div>
        </section>
      </div>

      <section class="flex flex-wrap items-center justify-between gap-4 rounded-[var(--sts-radius-card)] bg-sts-sand-soft p-6" aria-labelledby="booked-heading">
        <div>
          <h2 id="booked-heading" class="font-semibold">{{ copy.booking }}</h2>
          <p class="text-sm text-sts-muted">{{ copy.bookingBody }}</p>
        </div>
        <UiButton to="/my-booking" variant="secondary" icon="lucide:ticket">{{ copy.booking }}</UiButton>
      </section>
    </div>
  </main>
</template>
