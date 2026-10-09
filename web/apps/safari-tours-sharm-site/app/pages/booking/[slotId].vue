<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { calculateBookingTotal, formatMoney, multiplyMoney, type Booking, type TimeSlot } from "@wego/api-contract";
import { useCatalog } from "../../composables/useCatalog";
import { useAnalytics } from "../../composables/useAnalytics";
import { useDiscoveryCopy } from "../../composables/useDiscoveryCopy";
import { useSiteLocale } from "../../composables/useSiteLocale";
import {
  PublicApiError,
  createBooking,
  initiatePayment,
  storeBookingConfirmation,
} from "../../composables/usePublicToursApi";
import { ALL_NATIONALITIES, COMMON_NATIONALITIES, checkoutCopy, isPlausibleEmail, isPlausiblePhone, normalizePhone } from "../../content/checkout";
import { whatsappUrl } from "../../content/locales";
import { tourPageCopy } from "../../content/tourPage";
import { useSalesStatus } from "../../composables/useSalesStatus";
import { enquiryCopy } from "../../content/enquiry";
import { onlineSalesAvailable } from "../../utils/enquiry";

/**
 * Checkout: details → review → pay. Everything shown here is a preview; the
 * server prices the booking from the stored tour and re-checks capacity. No
 * personal data ever goes into the URL.
 */
const route = useRoute();
const locale = useSiteLocale();
const copy = computed(() => checkoutCopy[locale.value]);
const tourCopy = computed(() => tourPageCopy[locale.value]);
const discovery = useDiscoveryCopy();
const intlLocale = computed(() => (locale.value === "ar" ? "ar-EG" : locale.value));

useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm`, robots: "noindex" });
// Locale-dependent text (dates, country names) differs between the server's
// and browsers' ICU data, so it is rendered after hydration.
const mounted = useMounted();
const analytics = useAnalytics();
const { data: sales } = await useSalesStatus();
const online = computed(() => onlineSalesAvailable(sales.value));
const enquiry = computed(() => enquiryCopy[locale.value]);

// ── Trip from the tour page (non-personal query only) ──────────────────────
const slotId = computed(() => String(route.params.slotId));
function positiveInt(value: unknown, fallback: number) {
  const n = Number.parseInt(String(value ?? ""), 10);
  return Number.isSafeInteger(n) && n >= 0 && n <= 99 ? n : fallback;
}
const adults = computed(() => Math.max(1, positiveInt(route.query.adults, 1)));
const children = computed(() => positiveInt(route.query.children, 0));
const tourId = computed(() => (typeof route.query.tourId === "string" ? route.query.tourId : null));
const tourDate = computed(() => (typeof route.query.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date) ? route.query.date : null));
const timeSlot = computed(() => (typeof route.query.timeSlot === "string" ? (route.query.timeSlot as TimeSlot) : null));
const optionCode = computed(() => (typeof route.query.option === "string" ? route.query.option : null));
const units = computed(() => Math.min(50, positiveInt(route.query.units, 0)) || null);

const { data: catalog } = useCatalog();
const entry = computed(() => (catalog.value ?? []).find((e) => e.tour.id === tourId.value) ?? null);
const tour = computed(() => entry.value?.tour ?? null);
const priceOption = computed(() =>
  tour.value?.priceBasis === "PER_UNIT" ? tour.value.priceOptions.find((o) => o.code === optionCode.value) ?? null : null,
);
const total = computed(() => {
  if (!tour.value) return null;
  if (tour.value.priceBasis === "PER_UNIT") return priceOption.value && units.value ? multiplyMoney(priceOption.value.price, units.value) : null;
  try {
    return calculateBookingTotal(tour.value.priceAdult, adults.value, tour.value.priceChild, children.value);
  } catch {
    return null;
  }
});
const dateLabel = computed(() =>
  tourDate.value && mounted.value
    ? new Intl.DateTimeFormat(intlLocale.value, { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC" }).format(
        new Date(`${tourDate.value}T00:00:00Z`),
      )
    : null,
);
const guestsLabel = computed(() =>
  [tourCopy.value.booking.adultLine(adults.value), children.value ? tourCopy.value.booking.childLine(children.value) : null].filter(Boolean).join(" · "),
);
const unitLabel = computed(() =>
  priceOption.value && units.value
    ? tourCopy.value.booking.unitLine(units.value, tourCopy.value.booking.unitNames[priceOption.value.code] ?? priceOption.value.label)
    : null,
);
const tourLink = computed(() => (tour.value ? `/tour/${tour.value.slug}` : "/tours"));

// ── Form ───────────────────────────────────────────────────────────────────
type Field = "fullName" | "phone" | "nationality" | "email" | "hotelName";
const form = ref({ fullName: "", phone: "", nationality: "", email: "", hotelName: "", hotelRoom: "", specialRequests: "" });
const agreed = ref(false);
const errors = ref<Partial<Record<Field | "terms", string>>>({});
const step = ref<1 | 2>(1);
const submitting = ref(false);
const submitError = ref("");

// Country names come from the runtime's ICU data, which differs between the
// server and browsers, so the list is built in the browser only.
const nationalityOptions = computed(() => {
  if (!mounted.value) return [];
  const names = new Intl.DisplayNames([locale.value], { type: "region" });
  const label = (code: string) => {
    try {
      return names.of(code) ?? code;
    } catch {
      return code;
    }
  };
  const collator = new Intl.Collator(locale.value);
  const rest = ALL_NATIONALITIES.filter((c) => !COMMON_NATIONALITIES.includes(c))
    .map((code) => ({ value: code, label: label(code) }))
    .sort((a, b) => collator.compare(a.label, b.label));
  return [...COMMON_NATIONALITIES.map((code) => ({ value: code, label: label(code) })), { value: "--", label: "──────────" }, ...rest];
});
const nationalityName = computed(() => nationalityOptions.value.find((o) => o.value === form.value.nationality)?.label ?? form.value.nationality);

const reviewRows = computed(() =>
  [
    [copy.value.review.name, form.value.fullName],
    [copy.value.review.phone, form.value.phone],
    [copy.value.review.nationality, nationalityName.value],
    [copy.value.review.email, form.value.email],
    [copy.value.review.hotel, form.value.hotelName],
    [copy.value.review.room, form.value.hotelRoom],
    [copy.value.review.requests, form.value.specialRequests],
  ].filter(([, value]) => value),
);

const FIELD_LABELS = computed<Record<string, string>>(() => ({
  fullName: copy.value.details.fullName,
  phone: copy.value.details.phone,
  nationality: copy.value.details.nationality,
  email: copy.value.details.email,
  hotelName: copy.value.details.hotel,
}));
const errorList = computed(() =>
  (Object.entries(errors.value) as [string, string][])
    .filter(([field]) => field !== "terms")
    .map(([field, message]) => ({ fieldId: field, message: `${FIELD_LABELS.value[field] ?? field}: ${message}` })),
);

function validateDetails(): boolean {
  const e: typeof errors.value = {};
  if (!form.value.fullName.trim()) e.fullName = copy.value.errors.required;
  if (!form.value.phone.trim()) e.phone = copy.value.errors.required;
  else if (!isPlausiblePhone(form.value.phone)) e.phone = copy.value.errors.phone;
  if (!form.value.nationality || form.value.nationality === "--") e.nationality = copy.value.errors.required;
  if (form.value.email.trim() && !isPlausibleEmail(form.value.email)) e.email = copy.value.errors.email;
  if (!form.value.hotelName.trim()) e.hotelName = copy.value.errors.required;
  errors.value = e;
  return Object.keys(e).length === 0;
}

async function goToReview() {
  if (!validateDetails()) {
    await nextTick();
    document.querySelector<HTMLElement>("[data-error-summary]")?.focus();
    return;
  }
  step.value = 2;
  await nextTick();
  document.getElementById("review-heading")?.focus();
}

async function back() {
  created = null;
  step.value = 1;
  submitError.value = "";
  await nextTick();
  document.getElementById("details-heading")?.focus();
}

function messageFor(error: unknown): string {
  const e = copy.value.errors;
  if (!(error instanceof PublicApiError)) return e.network;
  if (error.status === 429) return e.tooMany;
  switch (error.errorCode) {
    case "slot_fully_booked":
      return e.slotFull;
    case "slot_blocked":
    case "slot_in_past":
      return e.slotBlocked;
    case "tour_not_active":
      return e.tourInactive;
    case "bookings_paused":
    case "payments_paused":
      return e.salesPaused;
    case "online_booking_unavailable":
    case "online_payment_unavailable":
      return enquiry.value.notice;
    case "payment_provider_error":
      return e.payment;
    case "guests_exceed_units":
    case "units_exceed_guests":
    case "price_option_required":
    case "unit_count_required":
    case "price_option_not_applicable":
    case "child_price_not_available":
      return e.pricing;
    default:
      return e.generic;
  }
}

// The booking created by this page, reused if paying is retried with the
// same details (e.g. after returning from Paymob or a payment-start error),
// so a retry never creates a second booking.
let created: { key: string; booking: Booking } | null = null;
let trackedCheckout: string | null = null;
function payloadKey() {
  return JSON.stringify([slotId.value, adults.value, children.value, optionCode.value, units.value, form.value]);
}
// A page restored from the back-forward cache must not stay "submitting".
function onPageShow(event: PageTransitionEvent) {
  if (event.persisted) submitting.value = false;
}
onMounted(() => window.addEventListener("pageshow", onPageShow));

onBeforeUnmount(() => window.removeEventListener("pageshow", onPageShow));

async function pay() {
  if (submitting.value) return; // one submission at a time
  if (!online.value) { submitError.value = enquiry.value.notice; return; }
  if (!agreed.value) {
    errors.value = { terms: copy.value.errors.terms };
    return;
  }
  errors.value = {};
  submitError.value = "";
  submitting.value = true;
  // Once per set of details: a retry after an error is not a new checkout.
  if (trackedCheckout !== payloadKey()) {
    trackedCheckout = payloadKey();
    analytics.track("begin_checkout", { item_id: tour.value?.slug, value: total.value ? Number(total.value.amount) : undefined, currency: total.value?.currencyCode });
  }
  try {
    const key = payloadKey();
    const booking = created?.key === key ? created.booking : await createBooking({
      slotId: slotId.value,
      adultsCount: adults.value,
      childrenCount: children.value,
      ...(priceOption.value && units.value ? { priceOptionCode: priceOption.value.code, unitCount: units.value } : {}),
      customer: {
        fullName: form.value.fullName.trim(),
        phone: normalizePhone(form.value.phone),
        nationality: form.value.nationality,
        email: form.value.email.trim() || undefined,
      },
      hotelName: form.value.hotelName.trim(),
      hotelRoom: form.value.hotelRoom.trim() || undefined,
      specialRequests: form.value.specialRequests.trim() || undefined,
      locale: locale.value,
    });
    created = { key, booking };
    // Tab-scoped handoff to the result pages; never in the URL.
    storeBookingConfirmation(booking);
    const payment = await initiatePayment(booking.id);
    // Only ever leave the site for an https payment page.
    if (new URL(payment.checkoutUrl).protocol !== "https:") throw new Error("unsafe_checkout_url");
    window.location.href = payment.checkoutUrl;
  } catch (error) {
    submitError.value = messageFor(error);
    submitting.value = false;
  }
}
</script>

<template>
  <main id="main-content" tabindex="-1" class="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:py-12">
    <h1 class="font-display text-3xl font-semibold">{{ online ? copy.title : enquiry.title }}</h1>

    <ol v-if="online" class="mt-6 flex items-center gap-2 text-sm font-semibold" :aria-label="copy.title">
      <li v-for="(label, i) in copy.steps" :key="label" class="flex flex-1 items-center gap-2" :aria-current="step === i + 1 ? 'step' : undefined">
        <span
          class="grid size-8 shrink-0 place-items-center rounded-full"
          :class="step > i + 1 ? 'bg-sts-palm text-white' : step === i + 1 ? 'bg-sts-ocean text-white' : 'bg-sts-border text-sts-muted'"
        >
          <Icon v-if="step > i + 1" name="lucide:check" class="size-4" aria-hidden="true" />
          <template v-else>{{ i + 1 }}</template>
        </span>
        <span class="hidden sm:inline" :class="step === i + 1 ? 'text-sts-ink' : 'text-sts-muted'">{{ label }}</span>
        <span v-if="i < 2" class="h-0.5 flex-1 rounded-full" :class="step > i + 1 ? 'bg-sts-palm' : 'bg-sts-border'" aria-hidden="true" />
      </li>
    </ol>

    <div v-if="!tourDate || !timeSlot || !tourId" class="mt-8">
      <UiEmptyState icon="lucide:calendar-x" :title="copy.errors.missingSlot">
        <UiButton to="/tours" variant="secondary">{{ discovery.category.all }}</UiButton>
      </UiEmptyState>
    </div>

    <div v-else-if="!online" class="mt-8 grid gap-3 rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-sand-soft p-4 text-sm" :role="sales?.bookingMode === 'ONLINE_PAYMENT' ? 'alert' : 'status'" data-enquiry-notice>
      <p>{{ sales?.bookingMode === 'ENQUIRY_ONLY' ? enquiry.notice : sales ? copy.errors.salesPaused : enquiry.unknown }}</p>
      <div class="flex flex-wrap gap-2">
        <UiButton :to="tourLink" variant="secondary" size="sm" icon="lucide:send" data-trip-enquiry>{{ enquiry.cta }}</UiButton>
        <UiButton :to="tourLink" variant="ghost" size="sm">{{ discovery.category.all }}</UiButton>
      </div>
    </div>

    <div v-else class="mt-8 grid gap-8 lg:grid-cols-[1fr_20rem]">
      <section v-if="step === 1" aria-labelledby="details-heading">
        <h2 id="details-heading" tabindex="-1" class="text-xl font-semibold">{{ copy.details.heading }}</h2>
        <p class="mt-1 text-sm text-sts-muted">{{ copy.details.intro }}</p>

        <UiErrorSummary v-if="errorList.length" data-error-summary class="mt-5" :title="copy.errors.summary" :errors="errorList" />

        <form class="mt-6 grid gap-5" novalidate @submit.prevent="goToReview">
          <UiField id="fullName" v-slot="f" :label="copy.details.fullName" :hint="copy.details.fullNameHint" :error="errors.fullName" required>
            <UiInput :id="f.id" v-model="form.fullName" autocomplete="name" maxlength="120" :aria-describedby="f.describedBy" :invalid="f.invalid" required />
          </UiField>
          <UiField id="phone" v-slot="f" :label="copy.details.phone" :hint="copy.details.phoneHint" :error="errors.phone" required>
            <UiInput :id="f.id" v-model="form.phone" type="tel" dir="ltr" inputmode="tel" autocomplete="tel" maxlength="32" :aria-describedby="f.describedBy" :invalid="f.invalid" required />
          </UiField>
          <UiField id="nationality" v-slot="f" :label="copy.details.nationality" :error="errors.nationality" required>
            <UiSelect :id="f.id" v-model="form.nationality" :options="nationalityOptions" :placeholder="copy.details.nationalityPlaceholder" :aria-describedby="f.describedBy" :invalid="f.invalid" required />
          </UiField>
          <UiField id="email" v-slot="f" :label="`${copy.details.email} (${copy.details.optional})`" :hint="copy.details.emailHint" :error="errors.email">
            <UiInput :id="f.id" v-model="form.email" type="email" dir="ltr" autocomplete="email" maxlength="200" :aria-describedby="f.describedBy" :invalid="f.invalid" />
          </UiField>
          <div class="grid gap-5 sm:grid-cols-[1fr_9rem]">
            <UiField id="hotelName" v-slot="f" :label="copy.details.hotel" :hint="copy.details.hotelHint" :error="errors.hotelName" required>
              <UiInput :id="f.id" v-model="form.hotelName" autocomplete="off" maxlength="200" :aria-describedby="f.describedBy" :invalid="f.invalid" required />
            </UiField>
            <UiField id="hotelRoom" v-slot="f" :label="`${copy.details.room} (${copy.details.optional})`">
              <UiInput :id="f.id" v-model="form.hotelRoom" autocomplete="off" maxlength="32" />
            </UiField>
          </div>
          <UiField id="specialRequests" v-slot="f" :label="`${copy.details.requests} (${copy.details.optional})`" :hint="copy.details.requestsHint">
            <UiTextarea :id="f.id" v-model="form.specialRequests" rows="3" maxlength="4000" :aria-describedby="f.describedBy" />
          </UiField>
          <UiButton type="submit" size="lg" block icon-end="lucide:arrow-right">{{ copy.details.continue }}</UiButton>
        </form>
      </section>

      <section v-else aria-labelledby="review-heading">
        <h2 id="review-heading" tabindex="-1" class="text-xl font-semibold">{{ copy.review.heading }}</h2>
        <dl class="mt-5 divide-y divide-sts-border rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface text-sm">
          <div v-for="row in reviewRows" :key="row[0]" class="flex justify-between gap-4 px-5 py-3">
            <dt class="text-sts-muted">{{ row[0] }}</dt>
            <dd class="text-end font-medium break-words">{{ row[1] }}</dd>
          </div>
        </dl>
        <UiButton variant="ghost" size="sm" icon="lucide:pencil" class="mt-2" @click="back">{{ copy.summary.change }}</UiButton>

        <div class="mt-5 rounded-[var(--sts-radius-card)] bg-sts-sand-soft p-4 text-sm">
          <p class="font-semibold">{{ copy.review.policy }}</p>
          <ul class="mt-1 grid gap-1 text-sts-muted">
            <li v-for="line in tourCopy.policy[tour?.cancellationPolicy ?? 'STANDARD']" :key="line">{{ line }}</li>
          </ul>
        </div>

        <div class="mt-5">
          <UiCheckbox id="agree" v-model="agreed" :invalid="Boolean(errors.terms)">
            {{ copy.review.terms[0] }}<NuxtLinkLocale to="/terms" class="text-sts-ocean-bright underline" target="_blank">{{ copy.review.terms[1] }}</NuxtLinkLocale>{{ copy.review.terms[2] }}<NuxtLinkLocale to="/privacy" class="text-sts-ocean-bright underline" target="_blank">{{ copy.review.terms[3] }}</NuxtLinkLocale>.
          </UiCheckbox>
          <p v-if="errors.terms" class="mt-1 text-xs font-medium text-sts-danger" role="alert">{{ errors.terms }}</p>
        </div>

        <div v-if="submitError" class="mt-5 grid gap-3 rounded-[var(--sts-radius-card)] border border-sts-danger/40 bg-sts-danger-soft p-4 text-sm text-sts-danger" role="alert">
          <p>{{ submitError }}</p>
          <div class="flex flex-wrap gap-2">
            <UiButton :to="tourLink" variant="secondary" size="sm">{{ copy.review.back }}</UiButton>
            <UiButton :href="whatsappUrl" variant="ghost" size="sm" icon="lucide:message-circle">{{ copy.errors.whatsapp }}</UiButton>
          </div>
        </div>

        <div class="mt-6 flex gap-3">
          <UiButton variant="secondary" size="lg" :disabled="submitting" @click="back">{{ copy.review.back }}</UiButton>
          <UiButton size="lg" class="flex-1" :loading="submitting" icon="lucide:lock" @click="pay">{{ copy.review.pay }}</UiButton>
        </div>
        <p class="mt-3 flex items-center gap-2 text-xs text-sts-muted">
          <Icon name="lucide:shield-check" class="size-4 text-sts-palm" aria-hidden="true" />{{ submitting ? copy.review.paying : copy.review.secure }}
        </p>
      </section>

      <aside class="order-first lg:order-none" :aria-label="copy.summary.heading">
        <div class="rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-5 shadow-sts-base lg:sticky lg:top-24">
          <h2 class="text-sm font-bold text-sts-muted">{{ copy.summary.heading }}</h2>
          <p class="mt-2 text-lg font-semibold" :lang="entry?.textLocale ?? 'en'">{{ entry?.name ?? "…" }}</p>
          <dl class="mt-4 grid gap-2 text-sm">
            <div v-if="dateLabel" class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.summary.date }}</dt><dd class="text-end">{{ dateLabel }}</dd></div>
            <div v-if="timeSlot" class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.summary.time }}</dt><dd>{{ discovery.tours.slots[timeSlot] ?? timeSlot }}</dd></div>
            <div class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.summary.guests }}</dt><dd class="text-end">{{ guestsLabel }}</dd></div>
            <div v-if="unitLabel" class="flex justify-between gap-3"><dt class="text-sts-muted">{{ copy.summary.units }}</dt><dd class="text-end">{{ unitLabel }}</dd></div>
            <div v-if="total" class="mt-2 flex justify-between gap-3 border-t border-sts-border pt-3 text-base font-bold">
              <dt>{{ copy.summary.total }}</dt><dd class="tabular-nums">{{ formatMoney(total) }}</dd>
            </div>
          </dl>
          <UiButton :to="tourLink" variant="ghost" size="sm" class="mt-3" icon="lucide:pencil">{{ copy.summary.change }}</UiButton>
        </div>
      </aside>
    </div>
  </main>
</template>
