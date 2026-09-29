<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import {
  clearAuthSession,
  logoutAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../../composables/useAuthSession";
import {
  cancelBooking,
  completeBooking,
  getBooking,
  formatMoney,
  ToursApiError,
  type Booking,
} from "../../composables/useToursApi";

useHead({ title: "Booking Detail · Safari Tours Sharm" });

const route  = useRoute();
const router = useRouter();

const session        = ref<AuthSession | null>(null);
const booking        = ref<Booking | null>(null);
const loadState      = ref<"idle" | "loading" | "loaded" | "error">("idle");
const loadError      = ref("");
const actionState    = ref<"idle" | "submitting" | "success" | "error">("idle");
const actionError    = ref("");
const cancelReason   = ref("");
const showCancelForm = ref(false);

const canCancel   = computed(() => hasPermission(session.value, "tours-operator.booking:cancel"));
const canComplete = computed(() => hasPermission(session.value, "tours-operator.booking:complete"));

const bookingId = computed(() => String(route.params.id));

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  loadState.value = "loading";
  loadError.value = "";
  try {
    booking.value   = await getBooking(session.value.token, bookingId.value);
    loadState.value = "loaded";
  } catch (err) {
    handleApiError(err);
    loadError.value = err instanceof ToursApiError ? err.errorCode : "Failed to load booking.";
    loadState.value = "error";
  }
}

async function doCancel() {
  if (!session.value || !booking.value) return;
  if (!cancelReason.value.trim()) {
    actionError.value = "Cancellation reason is required.";
    return;
  }
  actionState.value = "submitting";
  actionError.value = "";
  try {
    booking.value        = await cancelBooking(session.value.token, booking.value.id, cancelReason.value.trim());
    actionState.value    = "success";
    showCancelForm.value = false;
    cancelReason.value   = "";
  } catch (err) {
    handleApiError(err);
    actionError.value = err instanceof ToursApiError ? err.errorCode : "Action failed.";
    actionState.value = "error";
  }
}

async function doComplete() {
  if (!session.value || !booking.value) return;
  actionState.value = "submitting";
  actionError.value = "";
  try {
    booking.value     = await completeBooking(session.value.token, booking.value.id);
    actionState.value = "success";
  } catch (err) {
    handleApiError(err);
    actionError.value = err instanceof ToursApiError ? err.errorCode : "Action failed.";
    actionState.value = "error";
  }
}

async function logout() {
  await logoutAuthSession(session.value);
  void router.replace("/login");
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  void load();
});
</script>

<template>
  <main class="min-h-screen bg-sts-canvas px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <NuxtLink to="/bookings" class="text-sm text-sts-muted hover:text-sts-ocean">← Bookings</NuxtLink>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">
            Booking
            <span v-if="booking" class="ref text-sts-ocean">{{ booking.reference }}</span>
            <span v-else class="text-sts-muted">…</span>
          </h1>
        </div>
        <WegoButton type="button" variant="secondary" size="sm" class="text-sts-muted" @click="logout">
          Sign out
        </WegoButton>
      </header>

      <!-- Load error -->
      <WegoAlert v-if="loadState === 'error'" variant="danger" class="mt-6">{{ loadError }}</WegoAlert>

      <!-- Loading -->
      <p v-else-if="loadState === 'loading'" class="mt-6 text-sm text-sts-muted">Loading booking…</p>

      <!-- Booking detail -->
      <template v-else-if="booking">

        <!-- Action feedback -->
        <WegoAlert v-if="actionState === 'success'" variant="success" class="mt-6">
          Booking updated successfully.
        </WegoAlert>
        <WegoAlert v-if="actionState === 'error'" variant="danger" class="mt-6">
          {{ actionError }}
        </WegoAlert>

        <!-- Status + action buttons -->
        <div class="mt-6 flex flex-wrap items-center gap-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
          <div class="flex items-center gap-3">
            <span class="text-sm font-semibold text-sts-muted">Status</span>
            <span :class="`badge badge-${booking.status}`">{{ booking.status }}</span>
          </div>
          <div class="ms-auto flex flex-wrap gap-2">
            <WegoButton
              v-if="booking.status === 'CONFIRMED' && canComplete"
              type="button" variant="primary" size="sm"
              :disabled="actionState === 'submitting'"
              @click="doComplete"
            >
              {{ actionState === 'submitting' ? 'Completing…' : 'Mark completed' }}
            </WegoButton>
            <WegoButton
              v-if="(booking.status === 'NEW' || booking.status === 'CONFIRMED') && canCancel"
              type="button" variant="secondary" size="sm"
              class="border-sts-danger text-sts-danger hover:bg-sts-danger-soft"
              :disabled="actionState === 'submitting'"
              @click="showCancelForm = !showCancelForm"
            >
              Cancel booking
            </WegoButton>
          </div>
        </div>

        <!-- Cancel form -->
        <div v-if="showCancelForm" class="mt-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
          <p class="text-sm font-semibold mb-2">
            Cancellation reason <span class="text-sts-sunset">*</span>
          </p>
          <textarea
            v-model="cancelReason"
            rows="3"
            placeholder="Required — stored in the audit log"
            class="w-full rounded-xl border border-sts-border bg-sts-canvas px-4 py-2.5 text-sm focus:outline-sts-gold resize-none"
          />
          <div class="mt-3 flex gap-2">
            <WegoButton
              type="button" variant="secondary" size="sm"
              class="border-sts-danger text-sts-danger hover:bg-sts-danger-soft"
              :disabled="actionState === 'submitting' || !cancelReason.trim()"
              @click="doCancel"
            >
              {{ actionState === 'submitting' ? 'Cancelling…' : 'Confirm cancellation' }}
            </WegoButton>
            <WegoButton
              type="button" variant="secondary" size="sm"
              @click="showCancelForm = false; cancelReason = ''"
            >
              Dismiss
            </WegoButton>
          </div>
        </div>

        <!-- Details grid -->
        <div class="mt-6 grid gap-4 sm:grid-cols-2">

          <!-- Booking info -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">Booking details</h2>
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Reference</dt>
                <dd class="ref font-mono font-semibold text-sts-ocean">{{ booking.reference }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Tour date</dt>
                <dd>{{ booking.tourDate }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Time slot</dt>
                <dd>{{ booking.timeSlot }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Adults</dt>
                <dd class="tabular-nums">{{ booking.adultsCount }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Children</dt>
                <dd class="tabular-nums">{{ booking.childrenCount }}</dd>
              </div>
              <div class="flex justify-between gap-4 border-t border-sts-border pt-2">
                <dt class="text-sts-muted shrink-0">Total</dt>
                <dd class="money font-bold text-base">{{ formatMoney(booking.totalPrice) }}</dd>
              </div>
              <div class="flex justify-between gap-4 border-t border-sts-border pt-2">
                <dt class="text-sts-muted shrink-0">Created</dt>
                <dd>{{ new Date(booking.createdAt).toLocaleString() }}</dd>
              </div>
              <div v-if="booking.confirmedAt" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Confirmed</dt>
                <dd>{{ new Date(booking.confirmedAt).toLocaleString() }}</dd>
              </div>
              <div v-if="booking.completedAt" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Completed</dt>
                <dd>{{ new Date(booking.completedAt).toLocaleString() }}</dd>
              </div>
              <div v-if="booking.cancelledAt" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Cancelled</dt>
                <dd>{{ new Date(booking.cancelledAt).toLocaleString() }}</dd>
              </div>
              <div v-if="booking.cancellationReason" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Cancel reason</dt>
                <dd class="text-end">{{ booking.cancellationReason }}</dd>
              </div>
            </dl>
          </div>

          <!-- Customer info -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">Customer</h2>
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Name</dt>
                <dd class="font-medium text-end">{{ booking.customer.fullName }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Phone</dt>
                <dd>
                  <a
:href="`tel:${booking.customer.phone}`"
                    class="phone text-sts-ocean underline underline-offset-2">
                    {{ booking.customer.phone }}
                  </a>
                </dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Nationality</dt>
                <dd>{{ booking.customer.nationality }}</dd>
              </div>
              <div v-if="booking.customer.email" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Email</dt>
                <dd class="break-all text-end">{{ booking.customer.email }}</dd>
              </div>
              <div class="flex justify-between gap-4 border-t border-sts-border pt-2">
                <dt class="text-sts-muted shrink-0">Hotel</dt>
                <dd class="text-end">{{ booking.hotelName }}</dd>
              </div>
              <div v-if="booking.hotelRoom" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Room</dt>
                <dd>{{ booking.hotelRoom }}</dd>
              </div>
              <div v-if="booking.specialRequests" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Requests</dt>
                <dd class="text-end">{{ booking.specialRequests }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Locale</dt>
                <dd>{{ booking.locale }}</dd>
              </div>
            </dl>
          </div>

        </div>
      </template>

    </div>
  </main>
</template>
