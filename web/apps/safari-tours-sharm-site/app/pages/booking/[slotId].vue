<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import SiteHeader from "../../components/SiteHeader.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "../../content/locales";
import type { StsLocale } from "../../content/locales";
import {
  getTourPublic,
  createBooking,
  calculateBookingTotal,
  formatPrice,
  formatTimeSlot,
  cancellationDeadline,
  moneyToMinorUnits,
  storeBookingConfirmation,
  PublicApiError,
  type Tour,
  type BookingConfirmation,
} from "../../composables/usePublicToursApi";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];
const route  = useRoute();
const router = useRouter();

const locale    = useSiteLocale();
const copy      = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: "Book Your Tour — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
}));

// ── Route params ───────────────────────────────────────────────────────────
const slotId      = computed(() => route.params.slotId as string);
const adultsParam = computed(() => parseInt(String(route.query.adults  ?? "1")));
const childParam  = computed(() => parseInt(String(route.query.children ?? "0")));

// ── Step management ────────────────────────────────────────────────────────
// Step 1: customer details  Step 2: review  Step 3: submitting  Step 4: done
const step = ref<1 | 2 | 3 | 4>(1);

// ── Tour + slot metadata loaded for summary display ────────────────────────
const tour         = ref<Tour | null>(null);
const slotDate     = ref("");
const slotTimeSlot = ref("");

// We don't have a public "get slot by id" endpoint yet — we pass the slot
// info forward via the URL from the tour detail page.

onMounted(async () => {
  // The tour detail page passes tourId, date, timeSlot as query params
  const qDate     = route.query.date     as string | undefined;
  const qTimeSlot = route.query.timeSlot as string | undefined;
  const qTourId   = route.query.tourId   as string | undefined;

  if (qDate)     slotDate.value     = qDate;
  if (qTimeSlot) slotTimeSlot.value = qTimeSlot;

  if (qTourId) {
    try {
      tour.value = await getTourPublic(qTourId);
    } catch {
      // non-fatal — summary will show without tour name
    }
  }
});

// ── Form state ─────────────────────────────────────────────────────────────
const form = ref({
  fullName:    "",
  phone:       "",
  nationality: "EG",
  email:       "",
  hotelName:   "",
  hotelRoom:   "",
  specialRequests: "",
  agreedToTerms: false,
});

const fieldErrors = ref<Partial<Record<keyof typeof form.value, string>>>({});

const adultsCount   = ref(adultsParam.value);
const childrenCount = ref(childParam.value);

// ── Pricing ────────────────────────────────────────────────────────────────
const totalPrice = computed(() => {
  if (!tour.value) return null;
  try {
    return calculateBookingTotal(
      tour.value.priceAdult,
      adultsCount.value,
      tour.value.priceChild,
      childrenCount.value,
    );
  } catch {
    return null;
  }
});
const hasPositiveTotal = computed(
  () => totalPrice.value !== null && moneyToMinorUnits(totalPrice.value) > 0n,
);

// ── Common nationalities ───────────────────────────────────────────────────
const NATIONALITIES = [
  { code: "EG", name: "Egypt" },
  { code: "RU", name: "Russia" },
  { code: "DE", name: "Germany" },
  { code: "GB", name: "United Kingdom" },
  { code: "IT", name: "Italy" },
  { code: "FR", name: "France" },
  { code: "PL", name: "Poland" },
  { code: "UA", name: "Ukraine" },
  { code: "SA", name: "Saudi Arabia" },
  { code: "AE", name: "UAE" },
  { code: "US", name: "United States" },
  { code: "CN", name: "China" },
  { code: "IN", name: "India" },
  { code: "TR", name: "Turkey" },
  { code: "IL", name: "Israel" },
];

// ── Validation ─────────────────────────────────────────────────────────────
function validate(): boolean {
  const errs: typeof fieldErrors.value = {};
  if (!form.value.fullName.trim())    errs.fullName    = "Required";
  if (!form.value.phone.trim())       errs.phone       = "Required";
  if (!form.value.nationality)        errs.nationality = "Required";
  if (!form.value.hotelName.trim())   errs.hotelName   = "Required";
  fieldErrors.value = errs;
  return Object.keys(errs).length === 0;
}

function goToReview() {
  if (validate()) step.value = 2;
}

// ── Submit ─────────────────────────────────────────────────────────────────
const submitError = ref("");
const confirmation = ref<BookingConfirmation | null>(null);

async function submitBooking() {
  if (!form.value.agreedToTerms) {
    submitError.value = "Please agree to the Terms & Conditions.";
    return;
  }
  step.value = 3;
  submitError.value = "";

  try {
    const result = await createBooking({
      slotId:              slotId.value,
      adultsCount:         adultsCount.value,
      childrenCount:       childrenCount.value,
      customer: {
        fullName:    form.value.fullName,
        phone:       form.value.phone,
        nationality: form.value.nationality,
        email:       form.value.email || undefined,
      },
      hotelName:           form.value.hotelName,
      hotelRoom:           form.value.hotelRoom || undefined,
      specialRequests:     form.value.specialRequests || undefined,
      locale:              locale.value,
    });
    confirmation.value = result;
    storeBookingConfirmation(result);
    step.value = 4;
    // Redirect to confirmation page
    void router.replace(`/booking/confirmation?ref=${result.reference}`);
  } catch (err) {
    step.value = 2;
    if (err instanceof PublicApiError) {
      if (err.errorCode === "slot_fully_booked") {
        submitError.value = "Sorry, this slot just became fully booked. Please go back and choose another date.";
      } else if (err.errorCode === "slot_blocked") {
        submitError.value = "This slot is no longer available. Please choose another date.";
      } else if (err.errorCode === "tour_not_active") {
        submitError.value = "This tour is currently unavailable. Please contact us on WhatsApp.";
      } else {
        submitError.value = "Something went wrong. Please try again or book via WhatsApp.";
      }
    } else {
      submitError.value = "Could not connect to the server. Please try again.";
    }
  }
}
</script>

<template>
  <div :dir="direction" :lang="locale" class="min-h-screen bg-sts-canvas text-sts-ink">
    <SiteHeader
      :locale="locale"
      :direction="direction"
      :nav="copy.nav"
      :whatsapp-label="copy.whatsappFab"
      :current-locales="ALL_LOCALES"
      @set-locale="(l) => (locale = l)"
    />

    <main id="main-content" tabindex="-1" class="mx-auto max-w-2xl px-6 py-12 lg:px-0">

      <!-- ── Progress bar ────────────────────────────────────────── -->
      <nav aria-label="Booking steps" class="mb-8">
        <ol class="flex items-center gap-0">
          <li
            v-for="(label, i) in ['Details', 'Review', 'Payment']"
            :key="label"
            class="flex flex-1 items-center"
          >
            <div class="flex flex-col items-center gap-1">
              <div
                class="grid size-8 place-items-center rounded-full text-sm font-bold"
                :class="
                  step > i + 1
                    ? 'bg-green-500 text-white'
                    : step === i + 1
                      ? 'bg-sts-ocean text-white'
                      : 'bg-sts-border text-sts-muted'
                "
              >
                <span v-if="step > i + 1" aria-hidden="true">✓</span>
                <span v-else>{{ i + 1 }}</span>
              </div>
              <span
class="hidden text-xs font-medium sm:block"
                    :class="step === i + 1 ? 'text-sts-ocean' : 'text-sts-muted'">
                {{ label }}
              </span>
            </div>
            <div
v-if="i < 2" class="mx-2 h-0.5 flex-1 rounded-full"
                 :class="step > i + 1 ? 'bg-green-400' : 'bg-sts-border'" />
          </li>
        </ol>
      </nav>

      <!-- ── Booking summary ─────────────────────────────────────── -->
      <div
        v-if="tour || slotDate"
        class="mb-6 rounded-2xl border border-sts-border bg-sts-surface p-4 text-sm"
      >
        <p class="font-semibold text-sts-ink">
          {{ tour ? tour.slug.replace(/-/g, " ") : "Your Tour" }}
        </p>
        <p v-if="slotDate" class="mt-1 text-sts-muted">
          {{ formatDate(slotDate, locale) }}
          <span v-if="slotTimeSlot"> · {{ formatTimeSlot(slotTimeSlot as any) }}</span>
        </p>
        <p class="mt-1 text-sts-muted">
          {{ adultsCount }} adult<span v-if="adultsCount > 1">s</span>
          <span v-if="childrenCount > 0"> · {{ childrenCount }} child<span v-if="childrenCount > 1">ren</span></span>
        </p>
        <p v-if="hasPositiveTotal && totalPrice" class="mt-2 font-bold text-sts-ocean">
          Total: {{ formatPrice(totalPrice) }}
        </p>
      </div>

      <!-- ── STEP 1 — Your Details ───────────────────────────────── -->
      <section v-if="step === 1" aria-labelledby="step1-heading">
        <h1 id="step1-heading" class="text-2xl font-semibold">Your Details</h1>

        <form class="mt-6 space-y-4" novalidate @submit.prevent="goToReview">
          <!-- Full Name -->
          <div>
            <label for="fullName" class="block text-sm font-medium">
              Full Name <span class="text-red-500" aria-hidden="true">*</span>
            </label>
            <input
              id="fullName"
              v-model="form.fullName"
              type="text"
              autocomplete="name"
              class="mt-1 w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sts-ocean"
              :class="fieldErrors.fullName ? 'border-red-400' : 'border-sts-border'"
              placeholder="Ahmed Hassan"
            >
            <p v-if="fieldErrors.fullName" class="mt-1 text-xs text-red-500">{{ fieldErrors.fullName }}</p>
          </div>

          <!-- Phone -->
          <div>
            <label for="phone" class="block text-sm font-medium">
              Phone / WhatsApp <span class="text-red-500" aria-hidden="true">*</span>
            </label>
            <input
              id="phone"
              v-model="form.phone"
              type="tel"
              autocomplete="tel"
              class="mt-1 w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sts-ocean"
              :class="fieldErrors.phone ? 'border-red-400' : 'border-sts-border'"
              placeholder="+20 1234567890"
            >
            <p v-if="fieldErrors.phone" class="mt-1 text-xs text-red-500">{{ fieldErrors.phone }}</p>
          </div>

          <!-- Nationality -->
          <div>
            <label for="nationality" class="block text-sm font-medium">
              Nationality <span class="text-red-500" aria-hidden="true">*</span>
            </label>
            <select
              id="nationality"
              v-model="form.nationality"
              class="mt-1 w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sts-ocean"
              :class="fieldErrors.nationality ? 'border-red-400' : 'border-sts-border'"
            >
              <option value="">Select nationality…</option>
              <option
                v-for="n in NATIONALITIES"
                :key="n.code"
                :value="n.code"
              >
                {{ n.name }}
              </option>
            </select>
            <p v-if="fieldErrors.nationality" class="mt-1 text-xs text-red-500">{{ fieldErrors.nationality }}</p>
          </div>

          <!-- Email (optional) -->
          <div>
            <label for="email" class="block text-sm font-medium">
              Email <span class="text-sts-muted text-xs">(optional)</span>
            </label>
            <input
              id="email"
              v-model="form.email"
              type="email"
              autocomplete="email"
              class="mt-1 w-full rounded-xl border border-sts-border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sts-ocean"
              placeholder="ahmed@example.com"
            >
          </div>

          <!-- Hotel Name -->
          <div>
            <label for="hotelName" class="block text-sm font-medium">
              Hotel Name <span class="text-red-500" aria-hidden="true">*</span>
            </label>
            <input
              id="hotelName"
              v-model="form.hotelName"
              type="text"
              class="mt-1 w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sts-ocean"
              :class="fieldErrors.hotelName ? 'border-red-400' : 'border-sts-border'"
              placeholder="Hilton Sharm Dreams"
            >
            <p v-if="fieldErrors.hotelName" class="mt-1 text-xs text-red-500">{{ fieldErrors.hotelName }}</p>
          </div>

          <!-- Room (optional) -->
          <div>
            <label for="hotelRoom" class="block text-sm font-medium">
              Room Number <span class="text-sts-muted text-xs">(optional)</span>
            </label>
            <input
              id="hotelRoom"
              v-model="form.hotelRoom"
              type="text"
              class="mt-1 w-full rounded-xl border border-sts-border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sts-ocean"
              placeholder="312"
            >
          </div>

          <!-- Special requests -->
          <div>
            <label for="specialRequests" class="block text-sm font-medium">
              Special Requests <span class="text-sts-muted text-xs">(optional)</span>
            </label>
            <textarea
              id="specialRequests"
              v-model="form.specialRequests"
              rows="3"
              class="mt-1 w-full rounded-xl border border-sts-border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sts-ocean"
              placeholder="Vegetarian meal, wheelchair access…"
            />
          </div>

          <button
            type="submit"
            class="w-full rounded-2xl bg-sts-ocean py-4 font-semibold text-white shadow transition-transform hover:-translate-y-0.5"
          >
            Continue →
          </button>
        </form>
      </section>

      <!-- ── STEP 2 — Review & Confirm ──────────────────────────── -->
      <section v-else-if="step === 2" aria-labelledby="step2-heading">
        <h1 id="step2-heading" class="text-2xl font-semibold">Review Booking</h1>

        <dl class="mt-6 divide-y divide-sts-border rounded-2xl border border-sts-border bg-sts-surface text-sm">
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Name</dt>
            <dd class="font-medium">{{ form.fullName }}</dd>
          </div>
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Phone</dt>
            <dd class="font-medium">{{ form.phone }}</dd>
          </div>
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Nationality</dt>
            <dd class="font-medium">{{ NATIONALITIES.find(n => n.code === form.nationality)?.name ?? form.nationality }}</dd>
          </div>
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Hotel</dt>
            <dd class="font-medium">{{ form.hotelName }}{{ form.hotelRoom ? `, Room ${form.hotelRoom}` : "" }}</dd>
          </div>
          <div v-if="slotDate" class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Date</dt>
            <dd class="font-medium">{{ formatDate(slotDate, locale) }}</dd>
          </div>
          <div v-if="slotTimeSlot" class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Time</dt>
            <dd class="font-medium">{{ formatTimeSlot(slotTimeSlot as any) }}</dd>
          </div>
          <div class="flex justify-between px-5 py-3">
            <dt class="text-sts-muted">Travelers</dt>
            <dd class="font-medium">
              {{ adultsCount }} adult<span v-if="adultsCount > 1">s</span>
              <span v-if="childrenCount > 0"> + {{ childrenCount }} child<span v-if="childrenCount > 1">ren</span></span>
            </dd>
          </div>
          <div v-if="hasPositiveTotal && totalPrice" class="flex justify-between px-5 py-3.5">
            <dt class="font-semibold">Total</dt>
            <dd class="font-bold text-sts-ocean text-base">{{ formatPrice(totalPrice) }}</dd>
          </div>
        </dl>

        <!-- Cancellation note -->
        <p v-if="slotDate" class="mt-3 text-xs text-sts-muted">
          ✅ Free cancellation until {{ cancellationDeadline(slotDate) }}.
        </p>

        <!-- Error -->
        <div v-if="submitError" class="mt-4 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
          {{ submitError }}
        </div>

        <!-- Terms -->
        <label class="mt-5 flex cursor-pointer items-start gap-3">
          <input
            v-model="form.agreedToTerms"
            type="checkbox"
            class="mt-0.5 size-4 rounded accent-sts-ocean"
          >
          <span class="text-sm text-sts-muted">
            I agree to the
            <NuxtLink to="/terms" class="text-sts-ocean hover:underline">Terms & Conditions</NuxtLink>
            and
            <NuxtLink to="/privacy" class="text-sts-ocean hover:underline">Privacy Policy</NuxtLink>.
          </span>
        </label>

        <div class="mt-5 flex gap-3">
          <button
            type="button"
            class="flex-1 rounded-2xl border border-sts-border py-3.5 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
            @click="step = 1"
          >
            ← Back
          </button>
          <button
            type="button"
            class="flex-[2] rounded-2xl bg-sts-coral py-3.5 font-semibold text-white shadow transition-transform hover:-translate-y-0.5 disabled:opacity-40"
            :disabled="!form.agreedToTerms"
            @click="submitBooking"
          >
            Confirm & Pay →
          </button>
        </div>
      </section>

      <!-- ── STEP 3 — Submitting ─────────────────────────────────── -->
      <section
        v-else-if="step === 3"
        class="flex min-h-[40vh] flex-col items-center justify-center gap-4 text-center"
      >
        <div class="size-12 animate-spin rounded-full border-4 border-sts-ocean border-t-transparent" aria-hidden="true" />
        <p class="font-semibold text-sts-muted">Processing your booking…</p>
      </section>

      <!-- ── STEP 4 — Done (redirect in progress) ───────────────── -->
      <section
        v-else-if="step === 4"
        class="flex min-h-[40vh] flex-col items-center justify-center gap-4 text-center"
      >
        <p class="text-5xl" aria-hidden="true">✅</p>
        <p class="text-xl font-semibold text-green-700">Booking confirmed!</p>
        <p class="text-sm text-sts-muted">Redirecting…</p>
      </section>

      <!-- WhatsApp fallback always visible -->
      <div v-if="step <= 2" class="mt-8 text-center">
        <p class="text-sm text-sts-muted">Prefer to book directly?</p>
        <a
          :href="whatsappUrl"
          target="_blank"
          rel="noopener"
          class="mt-2 inline-flex items-center gap-2 text-sm font-semibold text-green-600 hover:underline"
        >
          💬 {{ copy.whatsappFab }}
        </a>
      </div>

    </main>

    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
