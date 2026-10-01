<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert } from "@wego/ui";
import { clearAuthSession, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { getSalesControl, ToursApiError, updateSalesControl, type SalesControl } from "../composables/useToursApi";

/**
 * Emergency switch: pause new online bookings and/or payment checkouts in
 * seconds, without a deploy. Payments already in progress, expiry and staff
 * work keep running.
 */
useHead({ title: "Online sales · Safari Tours Sharm" });

const router = useRouter();
const session = ref<AuthSession | null>(null);
const control = ref<SalesControl | null>(null);
const state = ref<"loading" | "loaded" | "error">("loading");
const error = ref("");
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

function fail(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
    return;
  }
  error.value =
    err instanceof ToursApiError && err.status === 403
      ? "You do not have permission to change online sales."
      : "Could not reach the server. Nothing was changed.";
}

async function load() {
  if (!session.value) return;
  state.value = "loading";
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
  error.value = "";
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
    fail(err);
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
      <h1 class="text-2xl font-semibold tracking-tight">Online sales</h1>
      <p class="mt-1 text-sm text-sts-muted">
        Emergency switch for the public website. Payments already in progress, booking expiry and staff work keep running.
      </p>

      <p v-if="state === 'loading'" class="mt-6 text-sm text-sts-muted">Loading…</p>
      <WegoAlert v-else-if="state === 'error'" variant="danger" class="mt-6">{{ error }}</WegoAlert>

      <template v-else-if="control">
        <div
          class="mt-6 rounded-2xl border px-5 py-4 text-sm font-semibold"
          :class="control.bookingsPaused || control.paymentsPaused
            ? 'border-sts-danger bg-sts-danger-soft text-sts-danger'
            : 'border-sts-border bg-sts-surface text-sts-success'"
          role="status"
        >
          <template v-if="control.bookingsPaused && control.paymentsPaused">Online bookings and payments are paused.</template>
          <template v-else-if="control.bookingsPaused">New online bookings are paused.</template>
          <template v-else-if="control.paymentsPaused">Online payments are paused.</template>
          <template v-else>Online sales are open.</template>
          <span v-if="control.updatedAt" class="mt-1 block text-xs font-normal text-sts-muted">
            Last changed {{ new Date(control.updatedAt).toLocaleString() }}
          </span>
        </div>

        <form class="mt-6 space-y-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-5 shadow-sm" @submit.prevent="save">
          <label class="flex items-start gap-3">
            <input v-model="bookingsPaused" type="checkbox" class="mt-1 size-4" >
            <span>
              <span class="block font-medium">Pause new online bookings</span>
              <span class="block text-sm text-sts-muted">Visitors can still browse tours; the website asks them to contact you instead.</span>
            </span>
          </label>
          <label class="flex items-start gap-3">
            <input v-model="paymentsPaused" type="checkbox" class="mt-1 size-4" >
            <span>
              <span class="block font-medium">Pause online payments</span>
              <span class="block text-sm text-sts-muted">No customer is sent to the card payment page. Unpaid bookings expire as usual.</span>
            </span>
          </label>
          <label class="block">
            <span class="block text-sm font-medium">Internal note (staff only, no customer data)</span>
            <input
              v-model="reason"
              type="text"
              maxlength="300"
              class="mt-1 w-full rounded-lg border border-sts-border bg-sts-canvas px-3 py-2 text-sm"
              placeholder="e.g. Payment provider outage"
            >
          </label>

          <WegoAlert v-if="error" variant="danger">{{ error }}</WegoAlert>
          <p v-if="saved && !dirty" class="text-sm text-sts-success" role="status">Saved — the website follows within seconds.</p>

          <div class="flex flex-wrap gap-3">
            <button
              type="submit"
              class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
              :disabled="saving || !dirty"
            >
              {{ saving ? "Saving…" : "Save" }}
            </button>
            <button
              type="button"
              class="rounded-lg bg-sts-danger px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
              :disabled="saving || (control.bookingsPaused && control.paymentsPaused)"
              @click="pauseAll"
            >
              Pause everything now
            </button>
          </div>
        </form>
      </template>
    </div>
  </main>
</template>
