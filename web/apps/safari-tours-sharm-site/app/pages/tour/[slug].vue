<script setup lang="ts">
import { computed, ref } from "vue";
import { formatMoney } from "@wego/api-contract";
import { useCatalog } from "../../composables/useCatalog";
import { useDiscoveryCopy } from "../../composables/useDiscoveryCopy";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { useTourPage } from "../../composables/useTourPage";
import { categoryMeta, siteCopy, siteWebsite, whatsappUrl } from "../../content/locales";
import { tourPageCopy } from "../../content/tourPage";
import { CATEGORY_VISUAL } from "../../utils/categoryVisual";
import { formatDuration } from "../../utils/tourDuration";

const route = useRoute();
const locale = useSiteLocale();
const copy = computed(() => tourPageCopy[locale.value]);
const discovery = useDiscoveryCopy();
const slug = () => String(route.params.slug);

const { data, error } = await useTourPage(slug);
if (error.value || !data.value) {
  // Unknown or inactive tour → real 404; anything else is a server error.
  const status = error.value?.statusCode === 404 ? 404 : 500;
  throw createError({ statusCode: status, statusMessage: status === 404 ? "Tour not found" : "Tour unavailable", fatal: true });
}
const { data: catalog } = useCatalog();

const tour = computed(() => data.value!.tour);
const content = computed(() => data.value?.content ?? null);
/** Language the tour text is really in (published translation or English fallback). */
const textLang = computed(() => content.value?.servedLocale ?? tour.value.localized?.locale ?? "en");
const name = computed(() => content.value?.name ?? tour.value.localized?.name ?? tour.value.nameEn ?? tour.value.slug.replace(/-/g, " "));
const summary = computed(() => content.value?.shortDescription ?? tour.value.localized?.shortDescription ?? null);
const paragraphs = computed(() => (content.value?.description ?? "").split(/\n{2,}/).map((p) => p.trim()).filter(Boolean));
const categoryName = computed(() => siteCopy[locale.value].categories[tour.value.category].name);
const visual = computed(() => CATEGORY_VISUAL[tour.value.category]);
const duration = computed(() => formatDuration(tour.value.durationText, locale.value));
const isTransfer = computed(() => tour.value.tourType === "TRANSFER");
// Transfers are priced per vehicle (sedan/SUV/minibus) but checkout still
// charges per person, so they are requested on WhatsApp until per-vehicle
// pricing lands in checkout (UX-4).
const onRequest = computed(() => tour.value.tourType === "REQUEST_ONLY" || tour.value.tourType === "TRANSFER");

const media = computed(() => content.value?.media ?? []);
const cover = computed(() => media.value.find((m) => m.isCover) ?? media.value[0] ?? null);
const facts = computed(() => content.value?.facts ?? null);
const stops = computed(() => (content.value?.stops ?? []).filter((stop) => stop.kind === "STOP"));
const meetingStop = computed(() => (content.value?.stops ?? []).find((stop) => stop.kind === "MEETING_POINT") ?? null);

const languageNames = computed(() => {
  const display = new Intl.DisplayNames([locale.value], { type: "language" });
  return (facts.value?.guideLanguages ?? []).map((code) => {
    try {
      return display.of(code) ?? code;
    } catch {
      return code;
    }
  });
});
const childrenText = computed(() => {
  const f = facts.value;
  if (!f || f.childrenAllowed == null) return null;
  if (!f.childrenAllowed) return copy.value.facts.childrenNo;
  return f.minimumAge ? copy.value.facts.minimumAge(f.minimumAge) : copy.value.facts.childrenYes;
});
const pickupText = computed(() => {
  const pickup = facts.value?.hotelPickup;
  if (pickup === "INCLUDED") return copy.value.facts.pickupIncluded;
  if (pickup === "NOT_INCLUDED") return copy.value.facts.pickupNotIncluded;
  if (pickup === "SOME_AREAS") return copy.value.facts.pickupSomeAreas;
  return null;
});
const quickFacts = computed(() =>
  [
    { icon: "lucide:clock", label: copy.value.facts.duration, value: duration.value },
    {
      icon: "lucide:sunrise",
      label: copy.value.facts.times,
      value: tour.value.availableTimeSlots.map((slot) => discovery.value.tours.slots[slot]).join(" · ") || null,
    },
    { icon: "lucide:baby", label: copy.value.facts.children, value: childrenText.value },
    { icon: "lucide:languages", label: copy.value.facts.languages, value: languageNames.value.join(" · ") || null },
    { icon: "lucide:bus", label: copy.value.facts.pickup, value: pickupText.value },
  ].filter((fact): fact is { icon: string; label: string; value: string } => Boolean(fact.value)),
);

const similar = computed(() =>
  (catalog.value ?? []).filter((entry) => entry.tour.category === tour.value.category && entry.tour.id !== tour.value.id).slice(0, 3),
);

const bookingOpen = ref(false);
// One booking card at a time: sticky aside on desktop, bottom sheet on mobile.
// Decided after hydration (the server can't know the viewport), so the
// server HTML and the first client render always match.
const mounted = useMounted();
const mediaDesktop = useMediaQuery("(min-width: 1024px)");
const isDesktop = computed(() => mounted.value && mediaDesktop.value);
const isMobile = computed(() => mounted.value && !mediaDesktop.value);
const whatsappRequest = computed(() =>
  `${whatsappUrl}?text=${encodeURIComponent(copy.value.booking.whatsappMessage(name.value, null, 1, 0))}`,
);

const pageUrl = computed(() => `${siteWebsite}/${locale.value}/tour/${tour.value.slug}`);
useSeoMeta({
  title: () => `${name.value} — Safari Tours Sharm`,
  description: () => summary.value ?? `${name.value} · ${categoryName.value} · ${duration.value} · ${discovery.value.card.from} ${formatMoney(tour.value.priceAdult)}`,
  ogTitle: () => name.value,
  ogDescription: () => summary.value ?? undefined,
  ogType: "website",
});
useHead(() => ({
  script: [
    {
      type: "application/ld+json",
      innerHTML: jsonLd([
        {
          "@context": "https://schema.org",
          "@type": "TouristTrip",
          name: name.value,
          description: summary.value ?? undefined,
          inLanguage: textLang.value,
          touristType: categoryName.value,
          provider: { "@type": "TravelAgency", name: "Safari Tours Sharm", url: siteWebsite },
          ...(onRequest.value
            ? {}
            : {
                offers: {
                  "@type": "Offer",
                  price: tour.value.priceAdult.amount,
                  priceCurrency: tour.value.priceAdult.currencyCode,
                  url: pageUrl.value,
                },
              }),
        },
        {
          "@context": "https://schema.org",
          "@type": "BreadcrumbList",
          itemListElement: [
            { "@type": "ListItem", position: 1, name: copy.value.breadcrumbHome, item: `${siteWebsite}/${locale.value}` },
            { "@type": "ListItem", position: 2, name: categoryName.value, item: `${siteWebsite}/${locale.value}/category/${categoryMeta[tour.value.category].slug}` },
            { "@type": "ListItem", position: 3, name: name.value, item: pageUrl.value },
          ],
        },
      ]),
    },
  ],
}));

/** JSON for a <script> tag: staff-written text can never close the tag. */
function jsonLd(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}
</script>

<template>
  <main id="main-content" tabindex="-1" class="pb-24 lg:pb-0">
    <div class="border-b border-sts-border bg-sts-surface">
      <nav class="mx-auto max-w-7xl px-4 py-3 text-sm text-sts-muted sm:px-6 lg:px-10" aria-label="Breadcrumb">
        <ol class="flex flex-wrap items-center gap-1.5">
          <li><NuxtLinkLocale to="/" class="hover:text-sts-ink">{{ copy.breadcrumbHome }}</NuxtLinkLocale></li>
          <li aria-hidden="true"><Icon name="lucide:chevron-right" class="size-3.5 rtl:-scale-x-100" /></li>
          <li><NuxtLinkLocale :to="`/category/${categoryMeta[tour.category].slug}`" class="hover:text-sts-ink">{{ categoryName }}</NuxtLinkLocale></li>
          <li aria-hidden="true"><Icon name="lucide:chevron-right" class="size-3.5 rtl:-scale-x-100" /></li>
          <li aria-current="page" class="font-semibold text-sts-ink" :lang="textLang">{{ name }}</li>
        </ol>
      </nav>
    </div>

    <div class="mx-auto grid max-w-7xl gap-10 px-4 py-8 sm:px-6 lg:grid-cols-[1fr_24rem] lg:px-10">
      <article class="min-w-0">
        <div class="overflow-hidden rounded-[var(--sts-radius-media)]" :style="{ viewTransitionName: `tour-media-${tour.slug}` }">
          <BrandTourMedia
            :src="cover?.path ?? null"
            :alt="cover?.alt ?? ''"
            :category="tour.category"
            ratio="16 / 9"
            priority
            sizes="(max-width: 1024px) 100vw, 60vw"
          />
        </div>

        <header class="mt-6">
          <div class="flex flex-wrap items-center gap-2 text-sm">
            <UiBadge :tone="visual.tone" :icon="visual.icon">{{ categoryName }}</UiBadge>
            <span class="inline-flex items-center gap-1 text-sts-muted"><Icon name="lucide:clock" class="size-4" aria-hidden="true" />{{ duration }}</span>
          </div>
          <h1 class="mt-3 font-display text-3xl leading-tight font-semibold tracking-tight text-balance sm:text-4xl" :lang="textLang">{{ name }}</h1>
          <p v-if="summary" class="mt-3 max-w-2xl text-lg leading-8 text-sts-muted" :lang="textLang">{{ summary }}</p>
        </header>

        <section v-if="quickFacts.length" class="mt-8" aria-labelledby="facts-heading">
          <h2 id="facts-heading" class="sr-only">{{ copy.facts.heading }}</h2>
          <dl class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            <div v-for="fact in quickFacts" :key="fact.label" class="flex items-start gap-3 rounded-[var(--sts-radius-control)] border border-sts-border bg-sts-surface p-4">
              <Icon :name="fact.icon" class="mt-0.5 size-5 shrink-0 text-sts-ocean-bright" aria-hidden="true" />
              <div>
                <dt class="text-xs font-semibold text-sts-muted">{{ fact.label }}</dt>
                <dd class="font-semibold">{{ fact.value }}</dd>
              </div>
            </div>
          </dl>
        </section>

        <section v-if="onRequest" class="mt-8 grid gap-3 rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-5 lg:hidden" aria-labelledby="request-heading">
          <h2 id="request-heading" class="text-lg font-semibold">{{ copy.requestOnly.heading }}</h2>
          <p class="text-sm text-sts-muted">{{ copy.requestOnly.body }}</p>
          <p v-if="tour.pricingNote" class="rounded-[var(--sts-radius-control)] bg-sts-sand-soft p-3 text-sm" lang="en">{{ tour.pricingNote }}</p>
        </section>

        <section class="mt-10" aria-labelledby="about-heading">
          <h2 id="about-heading" class="font-display text-2xl font-semibold">{{ copy.about }}</h2>
          <div v-if="paragraphs.length" class="mt-4 grid max-w-prose gap-4 leading-8" :lang="textLang">
            <p v-for="(paragraph, i) in paragraphs" :key="i">{{ paragraph }}</p>
          </div>
          <div v-else class="mt-4 flex items-start gap-3 rounded-[var(--sts-radius-control)] bg-sts-sand-soft p-4 text-sm">
            <Icon name="lucide:info" class="mt-0.5 size-5 shrink-0 text-sts-ocean-bright" aria-hidden="true" />
            <p>{{ copy.contentPending }}</p>
          </div>
        </section>

        <section v-if="stops.length" class="mt-10" aria-labelledby="itinerary-heading">
          <h2 id="itinerary-heading" class="font-display text-2xl font-semibold">{{ copy.itinerary }}</h2>
          <ol class="relative mt-5 grid gap-6 border-s-2 border-dashed border-sts-border ps-6" :lang="textLang">
            <li v-for="(stop, i) in stops" :key="stop.key" class="relative">
              <span class="absolute -start-[2.15rem] top-0 grid size-7 place-items-center rounded-full bg-sts-ocean text-xs font-bold text-white" aria-hidden="true">{{ i + 1 }}</span>
              <h3 class="font-semibold">{{ stop.name }}</h3>
              <p v-if="stop.description" class="mt-1 text-sm leading-6 text-sts-muted">{{ stop.description }}</p>
            </li>
          </ol>
        </section>

        <section v-if="content?.meetingPoint || meetingStop" class="mt-10" aria-labelledby="meeting-heading">
          <h2 id="meeting-heading" class="font-display text-2xl font-semibold">{{ copy.meetingPoint }}</h2>
          <p class="mt-3 flex items-start gap-2 leading-7" :lang="textLang">
            <Icon name="lucide:map-pin" class="mt-1 size-5 shrink-0 text-sts-coral" aria-hidden="true" />
            <span>{{ content?.meetingPoint ?? meetingStop?.name }}</span>
          </p>
        </section>

        <div v-if="content?.includes.length || content?.excludes.length" class="mt-10 grid gap-6 sm:grid-cols-2" :lang="textLang">
          <section v-if="content?.includes.length" aria-labelledby="includes-heading">
            <h2 id="includes-heading" class="text-lg font-semibold">{{ copy.includes }}</h2>
            <ul class="mt-3 grid gap-2">
              <li v-for="item in content.includes" :key="item" class="flex items-start gap-2 text-sm leading-6">
                <Icon name="lucide:check" class="mt-1 size-4 shrink-0 text-sts-success" aria-hidden="true" />{{ item }}
              </li>
            </ul>
          </section>
          <section v-if="content?.excludes.length" aria-labelledby="excludes-heading">
            <h2 id="excludes-heading" class="text-lg font-semibold">{{ copy.excludes }}</h2>
            <ul class="mt-3 grid gap-2">
              <li v-for="item in content.excludes" :key="item" class="flex items-start gap-2 text-sm leading-6">
                <Icon name="lucide:x" class="mt-1 size-4 shrink-0 text-sts-danger" aria-hidden="true" />{{ item }}
              </li>
            </ul>
          </section>
        </div>

        <section v-if="content?.knowBeforeYouGo.length" class="mt-10" aria-labelledby="know-heading">
          <h2 id="know-heading" class="font-display text-2xl font-semibold">{{ copy.know }}</h2>
          <ul class="mt-4 grid gap-2" :lang="textLang">
            <li v-for="item in content.knowBeforeYouGo" :key="item" class="flex items-start gap-2 text-sm leading-6">
              <Icon name="lucide:info" class="mt-1 size-4 shrink-0 text-sts-ocean-bright" aria-hidden="true" />{{ item }}
            </li>
          </ul>
        </section>

        <section v-if="media.length > 1" class="mt-10" aria-labelledby="gallery-heading">
          <h2 id="gallery-heading" class="font-display text-2xl font-semibold">{{ copy.gallery.heading }}</h2>
          <TourGallery class="mt-4" :media="media" />
        </section>

        <section v-if="copy.policy[tour.cancellationPolicy]" class="mt-10 rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6" aria-labelledby="policy-heading">
          <h2 id="policy-heading" class="flex items-center gap-2 text-lg font-semibold">
            <Icon name="lucide:shield-check" class="size-5 text-sts-palm" aria-hidden="true" />{{ copy.policy.heading }}
          </h2>
          <ul class="mt-3 grid gap-1.5 text-sm leading-6">
            <li v-for="line in copy.policy[tour.cancellationPolicy]" :key="line">{{ line }}</li>
          </ul>
        </section>
      </article>

      <aside class="hidden lg:block" :aria-label="onRequest ? copy.requestOnly.heading : copy.booking.heading">
        <div class="sticky top-24 rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6 shadow-sts-raised">
          <div v-if="onRequest" class="grid gap-3">
            <h2 class="text-lg font-semibold">{{ copy.requestOnly.heading }}</h2>
            <p v-if="isTransfer">
              <span class="text-xs text-sts-muted">{{ copy.booking.from }}</span>
              <span class="ms-1 text-2xl font-bold tabular-nums text-sts-ocean-bright">{{ formatMoney(tour.priceAdult) }}</span>
              <span class="ms-1 text-xs text-sts-muted">{{ copy.booking.perVehicle }}</span>
            </p>
            <p class="text-sm text-sts-muted">{{ copy.requestOnly.body }}</p>
            <p v-if="tour.pricingNote" class="rounded-[var(--sts-radius-control)] bg-sts-sand-soft p-3 text-sm" lang="en">{{ tour.pricingNote }}</p>
            <UiButton :href="whatsappRequest" icon="lucide:message-circle" block>{{ copy.requestOnly.cta }}</UiButton>
          </div>
          <TourBookingCard v-else-if="isDesktop" :tour="tour" :tour-name="name" :children-allowed="facts?.childrenAllowed ?? null" />
          <div v-else class="grid gap-3" aria-hidden="true">
            <UiSkeleton class="h-8 w-2/3" />
            <UiSkeleton class="h-64 w-full" />
            <UiSkeleton class="h-12 w-full" />
          </div>
        </div>
      </aside>
    </div>

    <section v-if="similar.length" class="bg-sts-sand-soft px-4 py-14 sm:px-6 lg:px-10" aria-labelledby="similar-heading">
      <div class="mx-auto max-w-7xl">
        <h2 id="similar-heading" class="font-display text-2xl font-semibold">{{ copy.similar }}</h2>
        <ul class="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <li v-for="entry in similar" :key="entry.tour.id"><TourCard :entry="entry" /></li>
        </ul>
      </div>
    </section>

    <div class="fixed inset-x-0 bottom-0 z-[var(--sts-z-sticky)] border-t border-sts-border bg-sts-surface/95 px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] backdrop-blur lg:hidden">
      <div class="mx-auto flex max-w-xl items-center justify-between gap-3">
        <p v-if="!onRequest">
          <span class="block text-xs text-sts-muted">{{ copy.booking.from }}</span>
          <span class="text-xl font-bold tabular-nums text-sts-ocean-bright">{{ formatMoney(tour.priceAdult) }}</span>
          <span class="ms-1 text-xs text-sts-muted">{{ isTransfer ? copy.booking.perVehicle : copy.booking.perPerson }}</span>
        </p>
        <UiButton v-if="onRequest" :href="whatsappRequest" icon="lucide:message-circle" block>{{ copy.requestOnly.cta }}</UiButton>
        <UiButton v-else icon="lucide:calendar" @click="bookingOpen = true">{{ copy.booking.mobileBar }}</UiButton>
      </div>
    </div>
    <UiSheet v-if="!onRequest && isMobile" v-model:open="bookingOpen" :title="name" :close-label="copy.booking.close">
      <TourBookingCard :tour="tour" :tour-name="name" :children-allowed="facts?.childrenAllowed ?? null" />
    </UiSheet>
  </main>
</template>
