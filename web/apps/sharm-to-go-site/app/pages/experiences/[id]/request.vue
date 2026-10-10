<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import GuestStepper from "../../../components/GuestStepper.vue";
import SiteSubHeader from "../../../components/SiteSubHeader.vue";
import { whatsappLink } from "../../../content/contact";
import { useSiteLocale } from "../../../composables/useSiteLocale";
import { useSalesStatus, salesCopy } from "../../../composables/useSalesStatus";
import {
  approximateUsdPrice,
  getPublicService,
  priceBasisLabel,
  type PublicService,
  type PublicServiceOption,
} from "../../../composables/usePublicCatalog";
import {
  createTravelRequest,
  newIdempotencyKey,
  travelRequestStatusText,
  TravelRequestError,
  type TravelRequestPublicResponse,
} from "../../../composables/useTravelRequests";

const route = useRoute();
const serviceId = String(route.params.id);

const { locale, copy, direction, toggleLocale } = useSiteLocale();
const { requestsOpen, loading: salesLoading, refresh: refreshSales, markPaused } = useSalesStatus();
// A lost response may hide a committed request. Permit retrying that exact
// attempt during a pause; the server returns its existing reference safely.
const attempted = ref(false);

useHead(() => ({
  title: locale.value === "ar" ? "اطلب التجربة · Sharm To Go" : "Request experience · Sharm To Go",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "robots", content: "noindex,nofollow" }],
}));

const service = ref<PublicService | null>(null);
const loadState = ref<"loading" | "loaded" | "not-found" | "error">("loading");

const selectedOptionIndex = ref(0);
const selectedOption = computed<PublicServiceOption | null>(() => service.value?.options[selectedOptionIndex.value] ?? null);

const step = ref<"party" | "contact" | "review" | "success">("party");

const minDate = computed(() => {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  return tomorrow.toISOString().slice(0, 10);
});

// Pre-filled from the homepage search box, forwarded through the catalog and
// detail pages as query params (see experiences/index.vue and
// experiences/[id].vue) — a convenience, not a capacity guarantee: the
// existing party-vs-capacity check below still runs on submit exactly as it
// would for a value the visitor typed here directly.
function queryNumber(value: unknown): number | null {
  if (typeof value !== "string") return null;
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

const requestedDate = ref(typeof route.query.date === "string" && route.query.date >= minDate.value ? route.query.date : "");
const adults = ref(queryNumber(route.query.adults) ?? 2);
const children = ref(queryNumber(route.query.children) ?? 0);
const hotelOrPickup = ref("");
const notes = ref("");
const partyError = ref(false);

const fullName = ref("");
const phone = ref("");
const email = ref("");
const consentGiven = ref(false);
const contactError = ref<"" | "required" | "invalidPhone" | "consentRequired">("");

// Same E.164-shaped plausibility check as the backend's
// TravelRequestCustomer.isPlausiblePhoneNumber — mirrored here so an
// obviously-wrong phone is caught before submit, not only after a round
// trip to the server. Still just a format check, not reachability proof.
function isPlausiblePhoneNumber(raw: string): boolean {
  const stripped = raw.replace(/[ ()\-.]/g, "");
  return /^\+?[1-9]\d{6,14}$/.test(stripped);
}

const submitState = ref<"idle" | "submitting" | "error">("idle");
const submitErrorCode = ref("");
const result = ref<TravelRequestPublicResponse | null>(null);
const copyState = ref<"idle" | "copied">("idle");

// Regenerated each time the review step is (re-)entered — not on every
// submit() call — so a plain retry from review (the only action available
// after a failed submit) reuses the same key as the real backend's
// idempotency protection expects, while going back to change party/contact
// details and returning to review correctly starts a new logical attempt.
// See newIdempotencyKey's own doc comment for why a fresh key per call
// would silently defeat that protection.
const idempotencyKey = ref(newIdempotencyKey());

onMounted(async () => {
  try {
    const found = await getPublicService(serviceId);
    if (found === null) {
      loadState.value = "not-found";
      return;
    }
    service.value = found;
    loadState.value = "loaded";
  } catch {
    loadState.value = "error";
  }
});

function goToContact() {
  const max = selectedOption.value?.maxParticipants ?? Infinity;
  if (adults.value + children.value > max || !requestedDate.value) {
    partyError.value = true;
    return;
  }
  partyError.value = false;
  step.value = "contact";
}

function goToReview() {
  if (!fullName.value.trim() || (!phone.value.trim() && !email.value.trim())) {
    contactError.value = "required";
    return;
  }
  if (phone.value.trim() && !isPlausiblePhoneNumber(phone.value)) {
    contactError.value = "invalidPhone";
    return;
  }
  if (!consentGiven.value) {
    contactError.value = "consentRequired";
    return;
  }
  contactError.value = "";
  idempotencyKey.value = newIdempotencyKey();
  attempted.value = false;
  step.value = "review";
}

async function submit() {
  if (!service.value || !selectedOption.value) return;
  if (submitState.value === "submitting" || (requestsOpen.value !== true && !attempted.value)) return;
  attempted.value = true;
  submitState.value = "submitting";
  submitErrorCode.value = "";
  try {
    const response = await createTravelRequest(
      {
        serviceId: service.value.id,
        serviceOptionId: selectedOption.value.id,
        requestedDate: requestedDate.value,
        adults: adults.value,
        children: children.value,
        hotelOrPickup: hotelOrPickup.value.trim() || undefined,
        locale: locale.value,
        notes: notes.value.trim() || undefined,
        sourceChannel: "WEBSITE",
        customer: {
          name: fullName.value.trim(),
          phone: phone.value.trim() || undefined,
          email: email.value.trim() || undefined,
        },
        expectedPriceAmount: selectedOption.value.priceAmount,
        expectedPriceCurrency: selectedOption.value.priceCurrency,
      },
      idempotencyKey.value,
    );
    result.value = response;
    step.value = "success";
    submitState.value = "idle";
  } catch (error) {
    submitState.value = "error";
    submitErrorCode.value = error instanceof TravelRequestError ? error.errorCode : "generic";
    if (submitErrorCode.value === "requests_paused") {
      attempted.value = false;
      markPaused();
    }
    if (submitErrorCode.value === "price_changed") {
      // Refetch so the review screen (bound to `selectedOption`, derived
      // from `service`) shows the real current price before the visitor
      // tries again — never resubmit the stale price silently.
      const refreshed = await getPublicService(serviceId).catch(() => null);
      if (refreshed) service.value = refreshed;
    }
  }
}

function errorMessage(code: string): string {
  if (code === "requests_paused") return salesCopy[locale.value].paused;
  if (code === "service_not_found") return copy.value.request.errorServiceNotFound;
  if (code === "option_not_found") return copy.value.request.errorOptionNotFound;
  if (code === "party_size_exceeds_capacity") return copy.value.request.errorPartyTooLarge;
  if (code === "price_changed") return copy.value.request.errorPriceChanged;
  return copy.value.request.errorGeneric;
}

const summaryText = computed(() => {
  if (!result.value || !service.value) return "";
  const r = result.value;
  return [
    `Sharm To Go — ${r.reference}`,
    `${r.serviceName[locale.value]} (${r.optionLabel[locale.value]})`,
    `${copy.value.request.reviewDate}: ${r.requestedDate}`,
    `${copy.value.request.reviewParty}: ${r.adults + r.children}`,
    `${copy.value.track.statusLabel}: ${travelRequestStatusText(r.status, locale.value)}`,
  ].join("\n");
});

async function copySummary() {
  await navigator.clipboard.writeText(summaryText.value);
  copyState.value = "copied";
  setTimeout(() => (copyState.value = "idle"), 2000);
}
</script>

<template>
  <main id="main-content" :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 pb-24 text-sharm-ink sm:pb-8 lg:px-10">
    <div class="mx-auto max-w-2xl">
      <SiteSubHeader
        :back-label="copy.request.backToService"
        :back-to="`/experiences/${serviceId}`"
        :direction="direction"
        :locale-label="copy.languageName"
        @toggle-locale="toggleLocale"
      />
    </div>

    <section class="mx-auto mt-8 max-w-2xl">
      <p v-if="loadState === 'loading'" class="text-sharm-muted">{{ copy.browse.loading }}</p>
      <div v-else-if="loadState === 'error'" role="alert" class="rounded-2xl border border-sharm-danger/30 bg-sharm-danger-soft p-6 text-sharm-danger">
        {{ copy.browse.loadError }}
      </div>
      <div v-else-if="loadState === 'not-found'" class="rounded-[2rem] border border-black/5 bg-sharm-surface p-8 text-center shadow-sm">
        <h1 class="text-2xl font-semibold">{{ copy.detail.notFoundHeading }}</h1>
        <NuxtLink to="/experiences" class="mt-6 inline-flex rounded-full bg-sharm-action px-6 py-3 font-semibold text-white">
          {{ copy.detail.back }}
        </NuxtLink>
      </div>

      <div v-else-if="service" class="rounded-[2rem] border border-black/5 bg-sharm-surface p-6 shadow-sm sm:p-8">
        <h1 class="font-display text-2xl font-semibold tracking-tight">{{ service.name[locale] }}</h1>

        <div v-if="step !== 'success' && requestsOpen !== true" role="status" class="mt-6 rounded-xl bg-sharm-lagoon p-4 text-sm text-sharm-action">
          <p>{{ requestsOpen === false ? salesCopy[locale].paused : salesCopy[locale].unknown }}</p>
          <button type="button" :disabled="salesLoading" class="mt-3 min-h-11 font-semibold underline disabled:opacity-60" @click="refreshSales">{{ salesCopy[locale].refresh }}</button>
          <a :href="whatsappLink(salesCopy[locale].contact)" target="_blank" rel="noopener" class="ms-4 font-semibold underline">{{ salesCopy[locale].contact }}</a>
        </div>

        <!-- Step indicator -->
        <ol v-if="step !== 'success'" class="mt-6 grid grid-cols-3 gap-2" :aria-label="copy.request.steps.partyDate">
          <li v-for="(label, key) in copy.request.steps" :key="key" class="min-w-0">
            <div
              class="h-1 rounded-full"
              :class="
                (key === 'partyDate' && ['party', 'contact', 'review'].includes(step)) ||
                (key === 'contact' && ['contact', 'review'].includes(step)) ||
                (key === 'review' && step === 'review')
                  ? 'bg-sharm-sea'
                  : 'bg-sharm-border'
              "
            />
            <span class="mt-2 block truncate text-xs font-semibold text-sharm-muted">{{ label }}</span>
          </li>
        </ol>

        <!-- Step 1: party & date -->
        <form v-if="step === 'party'" class="mt-8 space-y-6" @submit.prevent="goToContact">
          <h2 class="text-lg font-semibold">{{ copy.request.partyDateHeading }}</h2>

          <div v-if="service.options.length > 1">
            <label for="option" class="block text-sm font-semibold text-sharm-muted">{{ copy.request.optionLabel }}</label>
            <select
              id="option"
              v-model.number="selectedOptionIndex"
              class="mt-2 w-full min-h-12 rounded-xl border border-sharm-border px-4 font-normal"
            >
              <option v-for="(option, index) in service.options" :key="index" :value="index">
                {{ option.label[locale] }} — {{ option.priceCurrency }} {{ option.priceAmount }}
              </option>
            </select>
          </div>

          <div>
            <label for="date" class="block text-sm font-semibold text-sharm-muted">{{ copy.request.dateLabel }}</label>
            <input
              id="date"
              v-model="requestedDate"
              type="date"
              :min="minDate"
              required
              class="mt-2 w-full min-h-12 rounded-xl border border-sharm-border px-4 font-normal"
            >
            <p class="mt-1 text-xs text-sharm-muted">{{ copy.request.dateHelp }}</p>
          </div>

          <div class="grid gap-3 sm:grid-cols-2">
            <GuestStepper
              v-model:count="adults"
              :label="copy.request.adultsLabel"
              :minimum="1"
              :maximum="selectedOption?.maxParticipants ?? 12"
              :decrease-label="copy.request.decreaseGuestLabel"
              :increase-label="copy.request.increaseGuestLabel"
            />
            <GuestStepper
              v-model:count="children"
              :label="copy.request.childrenLabel"
              :minimum="0"
              :maximum="selectedOption?.maxParticipants ?? 12"
              :decrease-label="copy.request.decreaseGuestLabel"
              :increase-label="copy.request.increaseGuestLabel"
            />
          </div>

          <p v-if="partyError" role="alert" class="rounded-xl bg-sharm-danger-soft p-3 text-sm text-sharm-danger">
            {{ copy.request.partyExceedsCapacity(selectedOption?.maxParticipants ?? 0) }}
          </p>

          <div>
            <label for="pickup" class="block text-sm font-semibold text-sharm-muted">{{ copy.request.pickupLabel }}</label>
            <input
              id="pickup"
              v-model="hotelOrPickup"
              type="text"
              :placeholder="copy.request.pickupPlaceholder"
              class="mt-2 w-full min-h-12 rounded-xl border border-sharm-border px-4 font-normal"
            >
          </div>

          <div>
            <label for="notes" class="block text-sm font-semibold text-sharm-muted">{{ copy.request.notesLabel }}</label>
            <textarea id="notes" v-model="notes" rows="3" class="mt-2 w-full rounded-xl border border-sharm-border p-4 font-normal" />
          </div>

          <button type="submit" class="w-full min-h-12 rounded-full bg-sharm-action font-semibold text-white">
            {{ copy.request.continueButton }}
          </button>
        </form>

        <!-- Step 2: contact -->
        <form v-else-if="step === 'contact'" class="mt-8 space-y-6" @submit.prevent="goToReview">
          <h2 class="text-lg font-semibold">{{ copy.request.contactHeading }}</h2>

          <div>
            <label for="name" class="block text-sm font-semibold text-sharm-muted">{{ copy.request.nameLabel }}</label>
            <input id="name" v-model="fullName" type="text" autocomplete="name" required class="mt-2 w-full min-h-12 rounded-xl border border-sharm-border px-4 font-normal">
          </div>
          <div>
            <label for="phone" class="block text-sm font-semibold text-sharm-muted">{{ copy.request.phoneLabel }}</label>
            <input id="phone" v-model="phone" type="tel" autocomplete="tel" dir="ltr" class="mt-2 w-full min-h-12 rounded-xl border border-sharm-border px-4 font-normal">
          </div>
          <div>
            <label for="email" class="block text-sm font-semibold text-sharm-muted">{{ copy.request.emailLabel }}</label>
            <input id="email" v-model="email" type="email" autocomplete="email" dir="ltr" class="mt-2 w-full min-h-12 rounded-xl border border-sharm-border px-4 font-normal">
          </div>
          <p class="text-xs text-sharm-muted">{{ copy.request.contactHelp }}</p>

          <label class="flex items-start gap-3 text-sm leading-6">
            <input id="consent" v-model="consentGiven" type="checkbox" required class="mt-1 size-4 shrink-0">
            <span>
              {{ copy.request.consentLabelPrefix }}
              <NuxtLink to="/privacy" target="_blank" class="font-semibold text-sharm-sea underline">{{ copy.footerFull.legal.privacy }}</NuxtLink
              >{{ copy.request.consentLabelSuffix }}
            </span>
          </label>

          <p v-if="contactError" role="alert" class="rounded-xl bg-sharm-danger-soft p-3 text-sm text-sharm-danger">
            {{
              contactError === "invalidPhone"
                ? copy.request.contactPhoneInvalidError
                : contactError === "consentRequired"
                  ? copy.request.consentRequiredError
                  : copy.request.contactRequiredError
            }}
          </p>

          <div class="flex gap-3">
            <button type="button" class="min-h-12 rounded-full border border-sharm-border px-6 font-semibold" @click="step = 'party'">
              {{ copy.request.backButton }}
            </button>
            <button type="submit" class="flex-1 min-h-12 rounded-full bg-sharm-action font-semibold text-white">
              {{ copy.request.continueButton }}
            </button>
          </div>
        </form>

        <!-- Step 3: review -->
        <div v-else-if="step === 'review'" class="mt-8 space-y-6">
          <h2 class="text-lg font-semibold">{{ copy.request.reviewHeading }}</h2>
          <p class="rounded-xl bg-sharm-lagoon p-4 text-sm leading-6 text-sharm-action">{{ copy.request.reviewNote }}</p>

          <dl class="grid gap-3 text-sm">
            <div class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewService }}</dt><dd class="font-semibold">{{ service.name[locale] }} — {{ selectedOption?.label[locale] }}</dd></div>
            <div class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewDate }}</dt><dd class="font-semibold">{{ requestedDate }}</dd></div>
            <div class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewParty }}</dt><dd class="font-semibold">{{ adults + children }}</dd></div>
            <div v-if="hotelOrPickup" class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewPickup }}</dt><dd class="font-semibold">{{ hotelOrPickup }}</dd></div>
            <div v-if="selectedOption" class="flex justify-between gap-3">
              <dt class="text-sharm-muted">{{ copy.request.reviewPrice }}</dt>
              <dd class="font-semibold text-sharm-sea">
                {{ selectedOption.priceCurrency }} {{ selectedOption.priceAmount }}
                <span class="text-xs font-normal text-sharm-muted">
                  {{ copy.browse.approxUsd(approximateUsdPrice(selectedOption.priceAmount)) }}
                  {{ priceBasisLabel(selectedOption.priceBasis, copy.browse) }}
                </span>
              </dd>
            </div>
          </dl>

          <p v-if="submitState === 'error'" role="alert" class="rounded-xl bg-sharm-danger-soft p-3 text-sm text-sharm-danger">
            {{ errorMessage(submitErrorCode) }}
          </p>

          <div class="flex gap-3">
            <button type="button" class="min-h-12 rounded-full border border-sharm-border px-6 font-semibold" :disabled="submitState === 'submitting'" @click="step = 'contact'">
              {{ copy.request.backButton }}
            </button>
            <button type="button" class="flex-1 min-h-12 rounded-full bg-sharm-action font-semibold text-white disabled:opacity-60" :disabled="submitState === 'submitting' || (requestsOpen !== true && !attempted)" @click="submit">
              {{ submitState === "submitting" ? copy.request.submittingButton : copy.request.submitButton }}
            </button>
          </div>
        </div>

        <!-- Step 4: success -->
        <div v-else-if="step === 'success' && result" class="mt-8 space-y-6 text-center">
          <div class="mx-auto grid size-16 place-items-center rounded-full bg-sharm-success-soft text-3xl text-sharm-success">✓</div>
          <h2 class="font-display text-2xl font-semibold">
            {{ result.status === "CONFIRMED" || result.status === "COMPLETED" ? copy.request.successHeadingConfirmed : copy.request.successHeadingAwaiting }}
          </h2>
          <p class="leading-7 text-sharm-muted">
            {{ result.status === "CONFIRMED" || result.status === "COMPLETED" ? copy.request.successBodyConfirmed : copy.request.successBodyAwaiting }}
          </p>

          <div class="rounded-2xl border border-sharm-sea/25 bg-sharm-lagoon p-6">
            <p class="text-xs font-bold tracking-[0.12em] text-sharm-action uppercase">{{ copy.request.referenceLabel }}</p>
            <p class="reference mt-2 text-2xl font-semibold text-sharm-action">{{ result.reference }}</p>
          </div>

          <div class="flex flex-wrap justify-center gap-3">
            <button type="button" class="min-h-11 rounded-full border border-sharm-border px-5 text-sm font-semibold" @click="copySummary">
              {{ copyState === "copied" ? copy.request.copied : copy.request.copySummary }}
            </button>
            <a
              :href="whatsappLink(summaryText)"
              target="_blank"
              rel="noopener"
              class="inline-flex min-h-11 items-center rounded-full border border-sharm-border px-5 text-sm font-semibold"
            >
              {{ copy.request.whatsappShare }}
            </a>
          </div>

          <div class="flex flex-wrap justify-center gap-4 pt-2 text-sm">
            <NuxtLink :to="`/track/${result.reference}`" class="font-semibold text-sharm-sea underline">{{ copy.request.trackLink }}</NuxtLink>
            <NuxtLink to="/experiences" class="font-semibold text-sharm-sea underline">{{ copy.request.startOver }}</NuxtLink>
          </div>
        </div>
      </div>
    </section>
  </main>
</template>
