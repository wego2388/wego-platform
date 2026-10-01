<script setup lang="ts">
import { computed, ref } from "vue";
import { formatMoney } from "@wego/api-contract";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { useSiteLocale } from "../composables/useSiteLocale";
import { whatsappUrl } from "../content/locales";
import { myBookingCopy } from "../content/myBooking";
import { normalizePhone } from "../content/checkout";
import { tourPageCopy } from "../content/tourPage";
import { lookupBooking, PublicApiError, type PublicBookingLookup } from "../composables/usePublicToursApi";

/** Booking lookup by reference + phone. Both travel in a POST body, never the URL. */
const locale = useSiteLocale();
const copy = computed(() => myBookingCopy[locale.value]);
const tourCopy = computed(() => tourPageCopy[locale.value].booking);
const discovery = useDiscoveryCopy();
const intlLocale = computed(() => (locale.value === "ar" ? "ar-EG" : locale.value));
useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm`, description: () => copy.value.intro, robots: "noindex" });

const reference = ref("");
const phone = ref("");
const booking = ref<PublicBookingLookup | null>(null);
const state = ref<"idle" | "loading" | "loaded">("idle");
const error = ref("");

async function lookup() {
  if (state.value === "loading") return;
  error.value = "";
  if (!reference.value.trim() || !phone.value.trim()) {
    error.value = copy.value.required;
    return;
  }
  state.value = "loading";
  booking.value = null;
  try {
    booking.value = await lookupBooking(reference.value.trim().toUpperCase(), normalizePhone(phone.value));
    state.value = "loaded";
  } catch (err) {
    state.value = "idle";
    if (err instanceof PublicApiError && err.status === 404) error.value = copy.value.notFound;
    else if (err instanceof PublicApiError && err.status === 429) error.value = copy.value.tooMany;
    else error.value = copy.value.network;
  }
}

function reset() {
  booking.value = null;
  state.value = "idle";
  reference.value = "";
  phone.value = "";
}

const TONE = { NEW: "warning", CONFIRMED: "success", COMPLETED: "brand", CANCELLED: "danger", EXPIRED: "neutral" } as const;
const dateLabel = computed(() =>
  booking.value
    ? new Intl.DateTimeFormat(intlLocale.value, { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC" }).format(
        new Date(`${booking.value.tourDate}T00:00:00Z`),
      )
    : "",
);
const guests = computed(() =>
  booking.value
    ? [tourCopy.value.adultLine(booking.value.adultsCount), booking.value.childrenCount ? tourCopy.value.childLine(booking.value.childrenCount) : null]
        .filter(Boolean)
        .join(" · ")
    : "",
);
const unitLabel = computed(() => {
  const unit = booking.value?.unit;
  return unit ? tourCopy.value.unitLine(unit.unitCount, tourCopy.value.unitNames[unit.optionCode] ?? unit.optionLabel) : null;
});
const whatsappLink = computed(() =>
  booking.value ? `${whatsappUrl}?text=${encodeURIComponent(`${copy.value.result.heading}: ${booking.value.reference}`)}` : whatsappUrl,
);
</script>

<template>
  <main id="main-content" tabindex="-1" class="mx-auto max-w-xl px-4 py-12 sm:px-6">
    <h1 class="font-display text-3xl font-semibold">{{ copy.title }}</h1>
    <p class="mt-2 text-sts-muted">{{ copy.intro }}</p>

    <form v-if="state !== 'loaded'" class="mt-8 grid gap-5" novalidate @submit.prevent="lookup">
      <UiField id="ref" v-slot="f" :label="copy.reference" :hint="copy.referenceHint" required>
        <UiInput :id="f.id" v-model="reference" dir="ltr" class="font-mono uppercase" autocomplete="off" maxlength="64" :aria-describedby="f.describedBy" required />
      </UiField>
      <UiField id="lookup-phone" v-slot="f" :label="copy.phone" :hint="copy.phoneHint" required>
        <UiInput :id="f.id" v-model="phone" type="tel" dir="ltr" inputmode="tel" autocomplete="tel" maxlength="32" :aria-describedby="f.describedBy" required />
      </UiField>
      <p v-if="error" class="rounded-[var(--sts-radius-control)] bg-sts-danger-soft p-3 text-sm text-sts-danger" role="alert">{{ error }}</p>
      <UiButton type="submit" size="lg" block :loading="state === 'loading'" icon="lucide:search">{{ copy.submit }}</UiButton>
    </form>

    <section v-else-if="booking" class="mt-8" aria-labelledby="result-heading">
      <div class="overflow-hidden rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface shadow-sts-raised">
        <div class="flex flex-wrap items-center justify-between gap-3 bg-sts-ocean px-6 py-5 text-white">
          <div>
            <h2 id="result-heading" class="text-xs font-semibold tracking-wide text-white/70 uppercase">{{ copy.result.heading }}</h2>
            <p class="mt-1 font-mono text-2xl font-bold tracking-wider" dir="ltr">{{ booking.reference }}</p>
          </div>
          <UiBadge :tone="TONE[booking.status]">{{ copy.statuses[booking.status] }}</UiBadge>
        </div>
        <p class="border-b border-sts-border px-6 py-3 text-sm">{{ copy.statusHelp[booking.status] }}</p>
        <dl class="grid gap-3 px-6 py-5 text-sm">
          <div class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.result.date }}</dt><dd class="text-end font-medium">{{ dateLabel }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.result.time }}</dt><dd class="font-medium">{{ discovery.tours.slots[booking.timeSlot] ?? booking.timeSlot }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.result.guests }}</dt><dd class="text-end font-medium">{{ guests }}</dd></div>
          <div v-if="unitLabel" class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.result.units }}</dt><dd class="text-end font-medium">{{ unitLabel }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.result.hotel }}</dt><dd class="text-end font-medium">{{ booking.hotelName }}</dd></div>
          <div v-if="booking.cancellationReason" class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.result.reason }}</dt><dd class="text-end font-medium">{{ booking.cancellationReason }}</dd></div>
          <div class="flex justify-between gap-3 border-t border-dashed border-sts-border pt-3 text-base font-bold"><dt>{{ copy.result.total }}</dt><dd class="tabular-nums">{{ formatMoney(booking.totalPrice) }}</dd></div>
        </dl>
      </div>
      <p class="mt-6 text-sm text-sts-muted">{{ copy.help }}</p>
      <div class="mt-3 flex flex-wrap gap-3">
        <UiButton :href="whatsappLink" icon="lucide:message-circle">{{ copy.whatsapp }}</UiButton>
        <UiButton variant="secondary" @click="reset">{{ copy.another }}</UiButton>
      </div>
    </section>
  </main>
</template>
