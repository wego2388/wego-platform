<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import GuestStepper from "../components/GuestStepper.vue";
import PreviewDatePicker from "../components/PreviewDatePicker.vue";
import {
  bookingPreviewCopy,
  formatPreviewMoney,
  previewDates,
  previewPricing,
  type BookingStep,
  type PaymentMethodId,
} from "../content/booking-preview";
import { directionFor, type SharmLocale } from "../content/locales";

const locale = ref<SharmLocale>("en");
const direction = computed(() => directionFor(locale.value));
const copy = computed(() => bookingPreviewCopy[locale.value]);
const currentStep = ref<BookingStep>("options");
const selectedDateId = ref("2026-08-27");
const selectedLanguageId = ref("english");
const selectedTime = ref("08:30");
const adults = ref(2);
const children = ref(0);
const privatePickup = ref(false);
const fullName = ref("");
const email = ref("");
const phone = ref("");
const hotel = ref("");
const selectedPayment = ref<PaymentMethodId>("card");
const acceptedPolicy = ref(false);
const validationError = ref(false);
const cartMessage = ref(false);
const stepHeading = ref<HTMLElement | null>(null);

const stepOrder: BookingStep[] = ["options", "details", "payment", "complete"];
const selectedDate = computed(() => previewDates.find(date => date.id === selectedDateId.value) ?? previewDates[1]!);
const selectedGuideLanguage = computed(() => copy.value.guideOptions.find(option => option.id === selectedLanguageId.value)?.label ?? "—");
const adultTotal = computed(() => adults.value * (previewPricing.adult + selectedDate.value.priceDelta));
const childTotal = computed(() => children.value * previewPricing.child);
const addOnTotal = computed(() => privatePickup.value ? previewPricing.addOn : 0);
const total = computed(() => adultTotal.value + childTotal.value + addOnTotal.value);

useHead(() => ({
  title: locale.value === "ar" ? "نموذج الحجز · Sharm To Go" : "Booking prototype · Sharm To Go",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "robots", content: "noindex,nofollow" }],
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}

async function goTo(step: BookingStep) {
  currentStep.value = step;
  await nextTick();
  stepHeading.value?.focus();
}

function continueFlow() {
  validationError.value = false;
  if (currentStep.value === "options") {
    void goTo("details");
    return;
  }
  if (currentStep.value === "details") {
    if (!fullName.value.trim() || !phone.value.trim()) {
      validationError.value = true;
      return;
    }
    void goTo("payment");
    return;
  }
  if (currentStep.value === "payment" && acceptedPolicy.value) {
    void goTo("complete");
  }
}

function previousStep() {
  const index = stepOrder.indexOf(currentStep.value);
  const previous = stepOrder[index - 1];
  if (previous) void goTo(previous);
}

function restart() {
  acceptedPolicy.value = false;
  validationError.value = false;
  void goTo("options");
}
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas text-sharm-ink">
    <div class="border-b border-sharm-border bg-white">
      <header class="mx-auto flex max-w-7xl items-center justify-between gap-4 px-5 py-5 lg:px-10">
        <NuxtLink to="/experiences" class="flex items-center gap-3 font-semibold text-sharm-sea">
          <span aria-hidden="true">{{ direction === "rtl" ? "→" : "←" }}</span> {{ copy.back }}
        </NuxtLink>
        <button type="button" class="min-h-11 rounded-full border border-sharm-border px-4 text-sm font-semibold" @click="toggleLocale">
          {{ copy.switchLanguage }}
        </button>
      </header>
    </div>

    <div class="border-b border-amber-200 bg-sharm-warning-soft px-5 py-3 text-center text-sm font-semibold text-sharm-warning" role="status">
      {{ copy.prototypeNotice }}
    </div>

    <section class="sharm-hero border-b border-sharm-border">
      <div class="mx-auto grid max-w-7xl gap-8 px-5 py-10 lg:grid-cols-[1fr_22rem] lg:px-10 lg:py-14">
        <div>
          <span class="inline-flex rounded-full bg-white px-4 py-2 text-xs font-bold tracking-[0.1em] text-sharm-sea uppercase">{{ copy.prototype }}</span>
          <h1 class="mt-5 max-w-3xl text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.title }}</h1>
          <p class="mt-4 max-w-2xl leading-7 text-sharm-muted">{{ copy.subtitle }}</p>
        </div>
        <div class="rounded-[1.75rem] border border-white/80 bg-white/85 p-5 shadow-xl shadow-sharm-sea/10">
          <div class="h-28 rounded-2xl bg-[radial-gradient(circle_at_75%_25%,#f2a93b_0_8%,transparent_9%),linear-gradient(145deg,#075f67,#0b8691_58%,#d8f1ef)]" role="img" :aria-label="copy.exampleService" />
          <p class="mt-4 font-semibold">{{ copy.exampleService }}</p>
          <p class="money mt-2 text-sm font-semibold text-sharm-sea">{{ copy.from }} {{ formatPreviewMoney(locale, previewPricing.adult) }}</p>
        </div>
      </div>
    </section>

    <nav class="mx-auto max-w-7xl px-5 pt-7 lg:px-10" :aria-label="copy.prototype">
      <ol class="grid grid-cols-4 gap-2">
        <li v-for="(step, index) in stepOrder" :key="step" class="min-w-0">
          <div class="h-1 rounded-full" :class="stepOrder.indexOf(currentStep) >= index ? 'bg-sharm-sea' : 'bg-sharm-border'" />
          <span class="mt-2 block truncate text-xs font-semibold" :class="step === currentStep ? 'text-sharm-sea' : 'text-sharm-muted'" :aria-current="step === currentStep ? 'step' : undefined">
            {{ copy.stepLabels[step] }}
          </span>
        </li>
      </ol>
    </nav>

    <div v-if="currentStep !== 'complete'" class="mx-auto grid max-w-7xl gap-7 px-5 py-8 pb-32 lg:grid-cols-[minmax(0,1fr)_23rem] lg:px-10 lg:pb-16">
      <section class="min-w-0 rounded-[1.75rem] border border-sharm-border bg-white p-5 shadow-sm sm:p-7">
        <div v-if="currentStep === 'options'">
          <h2 ref="stepHeading" tabindex="-1" class="text-2xl font-semibold">{{ copy.dateHeading }}</h2>
          <p class="mt-2 text-sm text-sharm-muted">{{ copy.month }}</p>
          <div class="mt-6">
            <PreviewDatePicker
              :dates="previewDates"
              :from-label="copy.from"
              :locale="locale"
              :selected-id="selectedDateId"
              :unavailable-label="copy.unavailable"
              @select="selectedDateId = $event"
            />
          </div>

          <div class="mt-9 border-t border-sharm-border pt-7">
            <h3 class="text-lg font-semibold">{{ copy.guideHeading }}</h3>
            <div class="mt-4 flex flex-wrap gap-3">
              <button
                v-for="language in copy.guideOptions"
                :key="language.id"
                type="button"
                class="min-h-11 rounded-full border px-5 text-sm font-semibold"
                :class="selectedLanguageId === language.id ? 'border-sharm-sea bg-sharm-sea text-white' : 'border-sharm-border bg-white'"
                :aria-pressed="selectedLanguageId === language.id"
                @click="selectedLanguageId = language.id"
              >
                {{ language.label }}
              </button>
            </div>
          </div>

          <div class="mt-9 border-t border-sharm-border pt-7">
            <h3 class="text-lg font-semibold">{{ copy.timeHeading }}</h3>
            <div class="mt-4 flex flex-wrap gap-3" dir="ltr">
              <button
                v-for="time in copy.times"
                :key="time"
                type="button"
                class="min-h-11 rounded-xl border px-5 font-semibold"
                :class="selectedTime === time ? 'border-sharm-sea bg-sharm-lagoon text-sharm-sea' : 'border-sharm-border'"
                :aria-pressed="selectedTime === time"
                @click="selectedTime = time"
              >
                {{ time }}
              </button>
            </div>
          </div>

          <div class="mt-9 border-t border-sharm-border pt-7">
            <h3 class="text-lg font-semibold">{{ copy.guestsHeading }}</h3>
            <div class="mt-4 grid gap-3 sm:grid-cols-2">
              <GuestStepper v-model:count="adults" :decrease-label="copy.decrease" :increase-label="copy.increase" :label="copy.adults" :minimum="1" />
              <GuestStepper v-model:count="children" :decrease-label="copy.decrease" :increase-label="copy.increase" :label="copy.children" />
            </div>
          </div>

          <div class="mt-9 border-t border-sharm-border pt-7">
            <h3 class="text-lg font-semibold">{{ copy.addOnHeading }}</h3>
            <label class="mt-4 flex cursor-pointer items-start gap-4 rounded-2xl border border-sharm-border p-4">
              <input v-model="privatePickup" type="checkbox" class="mt-1 size-5 accent-sharm-sea">
              <span class="flex-1">
                <span class="block font-semibold">{{ copy.addOnName }}</span>
                <span class="mt-1 block text-sm text-sharm-muted">{{ copy.addOnBody }}</span>
              </span>
              <span class="money font-semibold text-sharm-sea">+{{ formatPreviewMoney(locale, previewPricing.addOn) }}</span>
            </label>
          </div>
          <div class="mt-6">
            <button type="button" class="min-h-11 rounded-full border border-sharm-border bg-white px-5 font-semibold text-sharm-sea" @click="cartMessage = true">
              {{ copy.addToCart }}
            </button>
            <p v-if="cartMessage" class="mt-3 rounded-xl bg-sharm-info-soft p-3 text-sm text-sharm-info" role="status">{{ copy.cartNotice }}</p>
          </div>
        </div>

        <div v-else-if="currentStep === 'details'">
          <h2 ref="stepHeading" tabindex="-1" class="text-2xl font-semibold">{{ copy.customerHeading }}</h2>
          <p class="mt-2 text-sharm-muted">{{ copy.customerBody }}</p>
          <div v-if="validationError" class="mt-5 rounded-2xl bg-sharm-danger-soft p-4 text-sm font-semibold text-sharm-danger" role="alert">
            {{ copy.requiredError }}
          </div>
          <div class="mt-7 grid gap-5 sm:grid-cols-2">
            <label class="grid gap-2 text-sm font-semibold">
              {{ copy.fullName }}
              <input v-model="fullName" type="text" autocomplete="name" class="min-h-12 rounded-xl border border-sharm-border px-4 font-normal" required>
            </label>
            <label class="grid gap-2 text-sm font-semibold">
              {{ copy.phone }}
              <input v-model="phone" type="tel" autocomplete="tel" dir="ltr" class="min-h-12 rounded-xl border border-sharm-border px-4 font-normal" required>
            </label>
            <label class="grid gap-2 text-sm font-semibold">
              {{ copy.email }} <span class="text-xs font-normal text-sharm-muted">({{ copy.optional }})</span>
              <input v-model="email" type="email" autocomplete="email" dir="ltr" class="min-h-12 rounded-xl border border-sharm-border px-4 font-normal">
            </label>
            <label class="grid gap-2 text-sm font-semibold">
              {{ copy.hotel }} <span class="text-xs font-normal text-sharm-muted">({{ copy.optional }})</span>
              <input v-model="hotel" type="text" class="min-h-12 rounded-xl border border-sharm-border px-4 font-normal">
            </label>
          </div>
        </div>

        <div v-else-if="currentStep === 'payment'">
          <h2 ref="stepHeading" tabindex="-1" class="text-2xl font-semibold">{{ copy.paymentHeading }}</h2>
          <p class="mt-2 max-w-2xl text-sharm-muted">{{ copy.paymentBody }}</p>
          <fieldset class="mt-7 grid gap-3">
            <legend class="sr-only">{{ copy.paymentHeading }}</legend>
            <label
              v-for="method in copy.paymentMethods"
              :key="method.id"
              class="flex cursor-pointer items-start gap-4 rounded-2xl border p-4"
              :class="selectedPayment === method.id ? 'border-sharm-sea bg-sharm-lagoon/45' : 'border-sharm-border'"
            >
              <input v-model="selectedPayment" type="radio" name="payment-method" :value="method.id" class="mt-1 size-5 accent-sharm-sea">
              <span>
                <span class="block font-semibold">{{ method.name }}</span>
                <span class="mt-1 block text-sm leading-6 text-sharm-muted">{{ method.detail }}</span>
              </span>
            </label>
          </fieldset>
          <p class="mt-5 rounded-2xl bg-sharm-info-soft p-4 text-sm leading-6 text-sharm-info">{{ copy.providerNote }}</p>
          <p class="mt-4 rounded-2xl bg-sharm-canvas p-4 text-sm leading-6 text-sharm-muted">{{ copy.secureNote }}</p>
          <label class="mt-6 flex cursor-pointer items-start gap-3 font-medium">
            <input v-model="acceptedPolicy" type="checkbox" class="mt-1 size-5 accent-sharm-sea">
            <span>{{ copy.policy }}</span>
          </label>
        </div>

        <div v-if="currentStep !== 'options'" class="mt-8 border-t border-sharm-border pt-6 lg:hidden">
          <button type="button" class="min-h-12 rounded-full border border-sharm-border px-5 font-semibold" @click="previousStep">
            {{ copy.previous }}
          </button>
        </div>
      </section>

      <aside class="hidden self-start rounded-[1.75rem] border border-sharm-border bg-white p-6 shadow-xl shadow-sharm-sea/8 lg:sticky lg:top-6 lg:block" aria-live="polite">
        <h2 class="text-xl font-semibold">{{ copy.summaryHeading }}</h2>
        <dl class="mt-6 grid gap-4 text-sm">
          <div class="flex justify-between gap-5"><dt class="text-sharm-muted">{{ copy.selectedDate }}</dt><dd class="reference font-semibold">{{ selectedDate.id }}</dd></div>
          <div class="flex justify-between gap-5"><dt class="text-sharm-muted">{{ copy.selectedTime }}</dt><dd class="reference font-semibold">{{ selectedTime }}</dd></div>
          <div class="flex justify-between gap-5"><dt class="text-sharm-muted">{{ copy.selectedLanguage }}</dt><dd class="font-semibold">{{ selectedGuideLanguage }}</dd></div>
          <div class="flex justify-between gap-5"><dt class="text-sharm-muted">{{ copy.adultsLine }} × {{ adults }}</dt><dd class="money font-semibold">{{ formatPreviewMoney(locale, adultTotal) }}</dd></div>
          <div v-if="children > 0" class="flex justify-between gap-5"><dt class="text-sharm-muted">{{ copy.childrenLine }} × {{ children }}</dt><dd class="money font-semibold">{{ formatPreviewMoney(locale, childTotal) }}</dd></div>
          <div v-if="privatePickup" class="flex justify-between gap-5"><dt class="text-sharm-muted">{{ copy.addOnLine }}</dt><dd class="money font-semibold">{{ formatPreviewMoney(locale, addOnTotal) }}</dd></div>
        </dl>
        <div class="mt-6 border-t border-sharm-border pt-5">
          <p class="text-sm text-sharm-muted">{{ copy.total }}</p>
          <p class="money mt-1 text-3xl font-semibold text-sharm-sea">{{ formatPreviewMoney(locale, total) }}</p>
          <p class="mt-2 text-xs text-sharm-warning">{{ copy.sampleAmount }}</p>
        </div>
        <div class="mt-6 grid gap-3">
          <button
            type="button"
            class="min-h-12 rounded-full bg-sharm-sea px-6 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-45"
            :disabled="currentStep === 'payment' && !acceptedPolicy"
            @click="continueFlow"
          >
            {{ currentStep === 'options' ? copy.continue : currentStep === 'details' ? copy.continueToPayment : copy.finishPrototype }}
          </button>
          <button v-if="currentStep !== 'options'" type="button" class="min-h-11 rounded-full border border-sharm-border px-5 font-semibold" @click="previousStep">
            {{ copy.previous }}
          </button>
        </div>
      </aside>

      <div class="fixed inset-x-0 bottom-0 z-20 border-t border-sharm-border bg-white/95 p-4 shadow-[0_-12px_36px_rgb(16_47_53_/_0.12)] backdrop-blur lg:hidden" aria-live="polite">
        <div class="mx-auto flex max-w-xl items-center justify-between gap-4">
          <div>
            <p class="text-xs text-sharm-muted">{{ copy.total }}</p>
            <p class="money text-xl font-semibold text-sharm-sea">{{ formatPreviewMoney(locale, total) }}</p>
            <p class="max-w-40 text-[0.65rem] leading-4 text-sharm-warning">{{ copy.sampleAmount }}</p>
          </div>
          <button
            type="button"
            class="min-h-12 rounded-full bg-sharm-sea px-5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-45"
            :disabled="currentStep === 'payment' && !acceptedPolicy"
            @click="continueFlow"
          >
            {{ currentStep === 'options' ? copy.continue : currentStep === 'details' ? copy.continueToPayment : copy.finishPrototype }}
          </button>
        </div>
      </div>
    </div>

    <section v-else class="mx-auto max-w-3xl px-5 py-16 text-center lg:px-10">
      <div class="rounded-[2rem] border border-sharm-border bg-white p-8 shadow-sm sm:p-12">
        <div class="mx-auto grid size-16 place-items-center rounded-full bg-sharm-success-soft text-3xl text-sharm-success" aria-hidden="true">✓</div>
        <h2 ref="stepHeading" tabindex="-1" class="mt-6 text-3xl font-semibold">{{ copy.completeHeading }}</h2>
        <p class="mx-auto mt-4 max-w-xl leading-7 text-sharm-muted">{{ copy.completeBody }}</p>
        <button type="button" class="mt-8 min-h-12 rounded-full bg-sharm-sea px-6 font-semibold text-white" @click="restart">{{ copy.restart }}</button>
      </div>
    </section>
  </main>
</template>
