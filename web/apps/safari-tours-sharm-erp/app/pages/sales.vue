<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert } from "@wego/ui";
import { clearAuthSession, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { getSalesControl, ToursApiError, updateSalesControl, type SalesControl } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import { operationsErrorMessage, salesSaveErrorMessage } from "../utils/operationsMessages";

/**
 * Emergency switch: pause new online bookings and/or payment checkouts in
 * seconds, without a deploy. Payments already in progress, expiry and staff
 * work keep running.
 */
const { t, instantLabel } = useErpLocale();
useHead(() => ({ title: `${t("sales.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const control = ref<SalesControl | null>(null);
const state = ref<"loading" | "loaded" | "error">("loading");
const error = ref<ErpMessageDescriptor | null>(null);
const saving = ref(false);
const saved = ref(false);

const bookingsPaused = ref(false);
const paymentsPaused = ref(false);
const reason = ref("");

const dirty = computed(
  () =>
    !!control.value &&
    (control.value.bookingsPaused !== bookingsPaused.value ||
      control.value.paymentsPaused !== paymentsPaused.value ||
      (control.value.reason ?? "") !== reason.value.trim()),
);

function apply(value: SalesControl) {
  control.value = value;
  bookingsPaused.value = value.bookingsPaused;
  paymentsPaused.value = value.paymentsPaused;
  reason.value = value.reason ?? "";
}

function fail(err: unknown, writing = false) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
    return;
  }
  error.value = writing ? salesSaveErrorMessage(err) : operationsErrorMessage(err);
}

async function load() {
  if (!session.value) return;
  state.value = "loading";
  error.value = null;
  saved.value = false;
  try {
    apply(await getSalesControl(session.value.token));
    state.value = "loaded";
  } catch (err) {
    fail(err);
    state.value = "error";
  }
}

async function save() {
  if (!session.value || saving.value) return;
  saving.value = true;
  saved.value = false;
  error.value = null;
  try {
    apply(
      await updateSalesControl(session.value.token, {
        bookingsPaused: bookingsPaused.value,
        paymentsPaused: paymentsPaused.value,
        reason: reason.value.trim() || null,
      }),
    );
    saved.value = true;
    window.dispatchEvent(new Event("sts:sales-control-changed"));
  } catch (err) {
    fail(err, true);
  } finally {
    saving.value = false;
  }
}

function pauseAll() {
  bookingsPaused.value = true;
  paymentsPaused.value = true;
  void save();
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) {
    void router.replace("/login");
    return;
  }
  void load();
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-2xl">
      <h1 class="text-2xl font-semibold tracking-tight">{{ t("sales.title") }}</h1>
      <p class="mt-1 text-sm text-sts-muted">
        {{ t("sales.subtitle") }}
      </p>

      <p v-if="state === 'loading'" class="mt-6 text-sm text-sts-muted" role="status">{{ t("common.loading") }}</p>
      <WegoAlert v-else-if="state === 'error' && error" variant="danger" class="mt-6">{{ t(error.key, error.params) }}</WegoAlert>

      <template v-else-if="control">
        <p v-if="control.bookingMode === 'ENQUIRY_ONLY'" class="mt-6 rounded-2xl border border-sts-border bg-sts-info-soft px-5 py-4 text-sm text-sts-info" role="status" data-enquiry-mode>{{ t("sales.enquiryMode") }}</p>
        <div
          class="mt-6 rounded-2xl border px-5 py-4 text-sm font-semibold"
          :class="control.bookingsPaused || control.paymentsPaused
            ? 'border-sts-danger bg-sts-danger-soft text-sts-danger'
            : 'border-sts-border bg-sts-surface text-sts-success'"
          role="status"
        >
          <template v-if="control.bookingMode === 'ENQUIRY_ONLY'">{{ t("sales.onlineUnavailable") }}</template>
          <template v-else-if="control.bookingsPaused && control.paymentsPaused">{{ t("sales.bothPaused") }}</template>
          <template v-else-if="control.bookingsPaused">{{ t("sales.bookingsPaused") }}</template>
          <template v-else-if="control.paymentsPaused">{{ t("sales.paymentsPaused") }}</template>
          <template v-else>{{ t("sales.open") }}</template>
          <span v-if="control.updatedAt" class="mt-1 block text-xs font-normal text-sts-muted">
            {{ t("sales.lastChanged", { date: instantLabel(control.updatedAt) }) }}
          </span>
        </div>

        <form class="mt-6 space-y-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-5 shadow-sm" @submit.prevent="save">
          <div class="flex items-start gap-3">
            <input id="pause-bookings" v-model="bookingsPaused" type="checkbox" class="mt-1 size-4" aria-describedby="pause-bookings-help" >
            <span>
              <label for="pause-bookings" class="block font-medium">{{ t("sales.pauseBookings") }}</label>
              <span id="pause-bookings-help" class="block text-sm text-sts-muted">{{ t("sales.bookingsHelp") }}</span>
            </span>
          </div>
          <div class="flex items-start gap-3">
            <input id="pause-payments" v-model="paymentsPaused" type="checkbox" class="mt-1 size-4" aria-describedby="pause-payments-help" >
            <span>
              <label for="pause-payments" class="block font-medium">{{ t("sales.pausePayments") }}</label>
              <span id="pause-payments-help" class="block text-sm text-sts-muted">{{ t("sales.paymentsHelp") }}</span>
            </span>
          </div>
          <label class="block">
            <span class="block text-sm font-medium">{{ t("sales.note") }}</span>
            <input
              v-model="reason"
              type="text"
              maxlength="300"
              class="mt-1 w-full rounded-lg border border-sts-border bg-sts-canvas px-3 py-2 text-sm"
              :placeholder="t('sales.notePlaceholder')"
            >
          </label>

          <WegoAlert v-if="error" variant="danger">{{ t(error.key, error.params) }}</WegoAlert>
          <p v-if="saved && !dirty" class="text-sm text-sts-success" role="status">{{ t("sales.saved") }}</p>

          <div class="flex flex-wrap gap-3">
            <button
              type="submit"
              class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
              :disabled="saving || !dirty"
            >
              {{ t(saving ? "sales.saving" : "sales.save") }}
            </button>
            <button
              type="button"
              class="rounded-lg bg-sts-danger px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
              :disabled="saving || (control.bookingsPaused && control.paymentsPaused)"
              @click="pauseAll"
            >
              {{ t("sales.pauseAll") }}
            </button>
            <button v-if="error" type="button" class="rounded-lg border border-sts-border px-4 py-2 text-sm font-semibold" :disabled="saving" @click="load">{{ t("common.refresh") }}</button>
          </div>
        </form>
      </template>
    </div>
  </main>
</template>
