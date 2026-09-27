<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import SiteHeader from "../../components/SiteHeader.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl, categoryMeta } from "../../content/locales";
import type { StsLocale } from "../../content/locales";
import {
  getTourBySlug,
  getAvailableSlots,
  calculateBookingTotal,
  formatPrice,
  formatTimeSlot,
  cancellationDeadline,
  moneyToMinorUnits,
  multiplyMoney,
  type Tour,
  type TourSlot,
  type TimeSlot,
  PublicApiError,
} from "../../composables/usePublicToursApi";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];
const route = useRoute();
const router = useRouter();

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

// ── Data ───────────────────────────────────────────────────────────────────

const tour = ref<Tour | null>(null);
const slots = ref<TourSlot[]>([]);
const loadState = ref<"loading" | "loaded" | "not_found" | "error">("loading");

// Booking card state
const selectedDate = ref("");
const selectedTimeSlot = ref<TimeSlot | "">("");
const adultsCount = ref(1);
const childrenCount = ref(0);

// Compute 60-day window from today
function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
function in60Days(): string {
  const d = new Date();
  d.setDate(d.getDate() + 60);
  return d.toISOString().slice(0, 10);
}

const slotsForDate = computed((): TourSlot[] => {
  if (!selectedDate.value) return [];
  return slots.value.filter(
    (s) => s.date === selectedDate.value && !s.isBlocked && s.available > 0,
  );
});

const selectedSlot = computed((): TourSlot | null => {
  if (!selectedTimeSlot.value) return null;
  return slotsForDate.value.find((s) => s.timeSlot === selectedTimeSlot.value) ?? null;
});

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
const adultSubtotal = computed(() =>
  tour.value ? multiplyMoney(tour.value.priceAdult, adultsCount.value) : null,
);
const childSubtotal = computed(() =>
  tour.value?.priceChild ? multiplyMoney(tour.value.priceChild, childrenCount.value) : null,
);

const canBook = computed(
  () =>
    selectedSlot.value !== null &&
    adultsCount.value >= 1 &&
    totalPrice.value !== null &&
    moneyToMinorUnits(totalPrice.value) > 0n,
);

// ── Load ───────────────────────────────────────────────────────────────────

async function load() {
  loadState.value = "loading";
  try {
    const slug = route.params.slug as string;
    tour.value = await getTourBySlug(slug);
    if (!tour.value) {
      loadState.value = "not_found";
      return;
    }
    // Fetch available slots for next 60 days
    slots.value = await getAvailableSlots(tour.value.id, todayIso(), in60Days());
    loadState.value = "loaded";
  } catch (err) {
    if (err instanceof PublicApiError && err.status === 404) {
      loadState.value = "not_found";
    } else {
      loadState.value = "error";
    }
  }
}

// Reset time slot when date changes
watch(selectedDate, () => {
  selectedTimeSlot.value = "";
});

onMounted(load);

// ── SEO ────────────────────────────────────────────────────────────────────

useHead(() => ({
  title: tour.value
    ? `${tour.value.slug.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase())} — Safari Tours Sharm`
    : "Tour — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: tour.value
    ? [
        {
          name: "description",
          content: `Book ${tour.value.slug.replace(/-/g, " ")} in Sharm El Sheikh from ${formatPrice(tour.value.priceAdult)}/person. Hotel pickup included.`,
        },
        { property: "og:title", content: tour.value.slug.replace(/-/g, " ") + " — Safari Tours Sharm" },
      ]
    : [],
}));

// ── Proceed to booking ─────────────────────────────────────────────────────

function proceedToBook() {
  if (!selectedSlot.value) return;
  void router.push({
    path: `/booking/${selectedSlot.value.id}`,
    query: {
      adults: String(adultsCount.value),
      children: String(childrenCount.value),
      tourId: selectedSlot.value.tourId,
      date: selectedSlot.value.date,
      timeSlot: selectedSlot.value.timeSlot,
    },
  });
}

// ── WhatsApp fallback ──────────────────────────────────────────────────────
const whatsappBookUrl = computed(() => {
  if (!tour.value) return whatsappUrl;
  const tourName = tour.value.slug.replace(/-/g, " ");
  const datePart = selectedDate.value ? ` on ${selectedDate.value}` : "";
  const childPart = childrenCount.value > 0 ? ` and ${childrenCount.value} child(ren)` : "";
  const msg = encodeURIComponent(
    `Hi, I'd like to book: ${tourName}${datePart} for ${adultsCount.value} adult(s)${childPart}.`,
  );
  return `${whatsappUrl}?text=${msg}`;
});
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

    <main id="main-content" tabindex="-1">

      <!-- ── Loading ──────────────────────────────────────────────── -->
      <div v-if="loadState === 'loading'" class="flex min-h-[60vh] items-center justify-center">
        <p class="text-sts-muted animate-pulse">Loading…</p>
      </div>

      <!-- ── Not found ────────────────────────────────────────────── -->
      <div
        v-else-if="loadState === 'not_found'"
        class="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-6 text-center"
      >
        <p class="text-5xl" aria-hidden="true">🗺️</p>
        <h1 class="text-2xl font-semibold">Tour not found</h1>
        <NuxtLink to="/tours" class="text-sts-ocean hover:underline">← Back to Tours</NuxtLink>
      </div>

      <!-- ── Error ─────────────────────────────────────────────────── -->
      <div
        v-else-if="loadState === 'error'"
        class="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-6 text-center"
      >
        <p class="text-2xl font-semibold text-red-600">Something went wrong</p>
        <a :href="whatsappUrl" target="_blank" rel="noopener" class="text-sts-ocean hover:underline">
          Book via WhatsApp instead
        </a>
      </div>

      <!-- ── Main content ──────────────────────────────────────────── -->
      <template v-else-if="tour">
        <!-- Breadcrumb -->
        <div class="border-b border-sts-border bg-sts-surface px-6 py-3 lg:px-10">
          <nav class="mx-auto flex max-w-7xl items-center gap-2 text-sm text-sts-muted" aria-label="Breadcrumb">
            <NuxtLink to="/" class="hover:text-sts-ink">Home</NuxtLink>
            <span aria-hidden="true">/</span>
            <NuxtLink
              :to="`/category/${categoryMeta[tour.category].slug}`"
              class="hover:text-sts-ink capitalize"
            >
              {{ copy.categories[tour.category].name }}
            </NuxtLink>
            <span aria-hidden="true">/</span>
            <span class="text-sts-ink capitalize">{{ tour.slug.replace(/-/g, " ") }}</span>
          </nav>
        </div>

        <!-- Two-column layout -->
        <div class="mx-auto max-w-7xl px-6 py-10 lg:px-10">
          <div class="lg:grid lg:grid-cols-[1fr_380px] lg:gap-12">

            <!-- ── LEFT — Content ──────────────────────────────────── -->
            <article>
              <!-- Image placeholder / gallery -->
              <div
                class="aspect-[16/9] w-full overflow-hidden rounded-2xl bg-sts-ocean/10 flex items-center justify-center"
                aria-label="Tour image"
              >
                <span class="text-6xl" aria-hidden="true">
                  {{ categoryMeta[tour.category].icon }}
                </span>
              </div>

              <!-- Title + meta -->
              <div class="mt-6 flex flex-wrap items-start justify-between gap-4">
                <div>
                  <span
                    class="inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-semibold"
                    :class="categoryMeta[tour.category].colorClass"
                  >
                    {{ categoryMeta[tour.category].icon }}
                    {{ copy.categories[tour.category].name }}
                  </span>
                  <h1 class="mt-3 font-display text-3xl font-semibold capitalize tracking-tight sm:text-4xl">
                    {{ tour.slug.replace(/-/g, " ") }}
                  </h1>
                  <div class="mt-2 flex flex-wrap gap-4 text-sm text-sts-muted">
                    <span>⏱ {{ tour.durationText }}</span>
                    <span>🏨 Hotel pickup included</span>
                    <span>✅ Free cancellation</span>
                  </div>
                </div>
                <div class="text-right">
                  <p class="text-sm text-sts-muted">From</p>
                  <p class="font-display text-3xl font-bold text-sts-ocean">
                    {{ formatPrice(tour.priceAdult) }}
                  </p>
                  <p class="text-xs text-sts-muted">per person</p>
                </div>
              </div>

              <!-- Available times -->
              <div class="mt-6 flex flex-wrap gap-2">
                <span
                  v-for="slot in tour.availableTimeSlots"
                  :key="slot"
                  class="rounded-full border border-sts-border bg-sts-surface px-3 py-1 text-xs font-semibold text-sts-ink"
                >
                  {{ formatTimeSlot(slot) }}
                </span>
              </div>

              <!-- What's included -->
              <section class="mt-8" aria-labelledby="includes-heading">
                <h2 id="includes-heading" class="text-lg font-semibold">What's included</h2>
                <ul class="mt-3 grid gap-2 sm:grid-cols-2">
                  <li class="flex items-center gap-2 text-sm text-sts-ink">
                    <span class="text-green-500" aria-hidden="true">✓</span> Hotel pickup & drop-off
                  </li>
                  <li class="flex items-center gap-2 text-sm text-sts-ink">
                    <span class="text-green-500" aria-hidden="true">✓</span> Air-conditioned transport
                  </li>
                  <li class="flex items-center gap-2 text-sm text-sts-ink">
                    <span class="text-green-500" aria-hidden="true">✓</span> Professional tour guide
                  </li>
                  <li class="flex items-center gap-2 text-sm text-sts-ink">
                    <span class="text-green-500" aria-hidden="true">✓</span> Entrance fees
                  </li>
                </ul>
              </section>

              <!-- Pricing breakdown -->
              <section class="mt-8 rounded-2xl border border-sts-border bg-sts-surface p-5">
                <h2 class="text-lg font-semibold">Pricing</h2>
                <dl class="mt-3 space-y-2 text-sm">
                  <div class="flex justify-between">
                    <dt>Adult</dt>
                    <dd class="font-semibold">{{ formatPrice(tour.priceAdult) }} / person</dd>
                  </div>
                  <div v-if="tour.priceChild" class="flex justify-between">
                    <dt>Child</dt>
                    <dd class="font-semibold">{{ formatPrice(tour.priceChild) }} / person</dd>
                  </div>
                  <div v-else class="flex justify-between text-sts-muted">
                    <dt>Child</dt>
                    <dd>Contact us</dd>
                  </div>
                </dl>
              </section>

              <!-- Cancellation policy -->
              <section class="mt-6 rounded-2xl border border-green-200 bg-green-50 p-5">
                <h2 class="flex items-center gap-2 font-semibold text-green-800">
                  <span aria-hidden="true">✅</span> Free Cancellation
                </h2>
                <p class="mt-2 text-sm text-green-700">
                  {{ copy.cancellationBody }}
                </p>
              </section>
            </article>

            <!-- ── RIGHT — Booking card (sticky) ──────────────────── -->
            <aside class="mt-10 lg:mt-0">
              <div class="sticky top-6 rounded-2xl border border-sts-border bg-sts-surface p-6 shadow-lg">
                <h2 class="text-xl font-semibold">Book this tour</h2>

                <!-- Date picker -->
                <div class="mt-4">
                  <label for="tour-date" class="block text-sm font-medium text-sts-muted">
                    Select date
                  </label>
                  <input
                    id="tour-date"
                    v-model="selectedDate"
                    type="date"
                    :min="todayIso()"
                    :max="in60Days()"
                    class="mt-1 w-full rounded-xl border border-sts-border bg-white px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sts-ocean"
                  >
                </div>

                <!-- Time slot -->
                <div class="mt-4">
                  <label class="block text-sm font-medium text-sts-muted">Time slot</label>
                  <div v-if="!selectedDate" class="mt-2 text-sm text-sts-muted">
                    Please select a date first.
                  </div>
                  <div v-else-if="slotsForDate.length === 0" class="mt-2 text-sm text-sts-muted">
                    No available slots on this date.
                  </div>
                  <div v-else class="mt-2 flex flex-wrap gap-2">
                    <button
                      v-for="slot in slotsForDate"
                      :key="slot.id"
                      type="button"
                      class="rounded-xl border px-4 py-2 text-sm font-medium transition-colors"
                      :class="
                        selectedTimeSlot === slot.timeSlot
                          ? 'border-sts-ocean bg-sts-ocean text-white'
                          : 'border-sts-border bg-white text-sts-ink hover:border-sts-ocean'
                      "
                      @click="selectedTimeSlot = slot.timeSlot"
                    >
                      {{ formatTimeSlot(slot.timeSlot) }}
                      <span class="ml-1 text-xs opacity-70">{{ slot.available }} left</span>
                    </button>
                  </div>
                </div>

                <!-- Pax counters -->
                <div class="mt-4 grid grid-cols-2 gap-3">
                  <!-- Adults -->
                  <div class="rounded-xl border border-sts-border p-3">
                    <p class="text-xs font-medium text-sts-muted">Adults</p>
                    <div class="mt-2 flex items-center justify-between">
                      <button
                        type="button"
                        aria-label="Decrease adults"
                        class="grid size-8 place-items-center rounded-lg border border-sts-border text-sts-ink disabled:opacity-30"
                        :disabled="adultsCount <= 1"
                        @click="adultsCount = Math.max(1, adultsCount - 1)"
                      >
                        −
                      </button>
                      <span class="font-semibold">{{ adultsCount }}</span>
                      <button
                        type="button"
                        aria-label="Increase adults"
                        class="grid size-8 place-items-center rounded-lg border border-sts-border text-sts-ink"
                        @click="adultsCount++"
                      >
                        +
                      </button>
                    </div>
                  </div>
                  <!-- Children -->
                  <div class="rounded-xl border border-sts-border p-3">
                    <p class="text-xs font-medium text-sts-muted">Children</p>
                    <div class="mt-2 flex items-center justify-between">
                      <button
                        type="button"
                        aria-label="Decrease children"
                        class="grid size-8 place-items-center rounded-lg border border-sts-border text-sts-ink disabled:opacity-30"
                        :disabled="childrenCount <= 0"
                        @click="childrenCount = Math.max(0, childrenCount - 1)"
                      >
                        −
                      </button>
                      <span class="font-semibold">{{ childrenCount }}</span>
                      <button
                        type="button"
                        aria-label="Increase children"
                        class="grid size-8 place-items-center rounded-lg border border-sts-border text-sts-ink"
                        @click="childrenCount++"
                      >
                        +
                      </button>
                    </div>
                  </div>
                </div>

                <!-- Price breakdown -->
                <div v-if="selectedSlot" class="mt-4 rounded-xl bg-sts-canvas p-4 text-sm">
                  <div class="flex justify-between text-sts-muted">
                    <span>{{ adultsCount }}× adult @ {{ formatPrice(tour.priceAdult) }}</span>
                    <span v-if="adultSubtotal">{{ formatPrice(adultSubtotal) }}</span>
                  </div>
                  <div v-if="childrenCount > 0 && tour.priceChild && childSubtotal" class="flex justify-between text-sts-muted">
                    <span>{{ childrenCount }}× child @ {{ formatPrice(tour.priceChild) }}</span>
                    <span>{{ formatPrice(childSubtotal) }}</span>
                  </div>
                  <div class="mt-2 flex justify-between border-t border-sts-border pt-2 font-bold text-sts-ink">
                    <span>Total</span>
                    <span v-if="totalPrice" class="text-sts-ocean">{{ formatPrice(totalPrice) }}</span>
                    <span v-else class="text-red-600">Child price unavailable</span>
                  </div>
                  <p class="mt-2 text-xs text-sts-muted">
                    Free cancellation until {{ cancellationDeadline(selectedSlot.date) }}
                  </p>
                </div>

                <!-- CTA -->
                <button
                  type="button"
                  class="mt-4 w-full rounded-2xl bg-sts-coral py-4 font-semibold text-white shadow-md transition-transform hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-40"
                  :disabled="!canBook"
                  @click="proceedToBook"
                >
                  {{ copy.hero.cta }}
                </button>

                <!-- WhatsApp fallback -->
                <a
                  :href="whatsappBookUrl"
                  target="_blank"
                  rel="noopener"
                  class="mt-3 flex w-full items-center justify-center gap-2 rounded-2xl border border-sts-border py-3.5 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
                >
                  <span aria-hidden="true">💬</span> {{ copy.whatsappFab }}
                </a>
              </div>
            </aside>
          </div>
        </div>
      </template>

    </main>

    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
