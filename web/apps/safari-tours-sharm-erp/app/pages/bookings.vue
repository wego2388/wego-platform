<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import {
  cancelBooking,
  completeBooking,
  listBookings,
  listAllStaffTours,
  formatMoney,
  ToursApiError,
  type Booking,
  type BookingStatus,
  type Tour,
  PAGE_SIZE,
} from "../composables/useToursApi";

useHead({ title: "Bookings · Safari Tours Sharm" });

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const bookings   = ref<Booking[]>([]);
const toursById  = ref<Record<string, Tour>>({});
const listState  = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError  = ref("");
const page       = ref(0);
const hasNext    = ref(false);

// Filters
const filterStatus = ref<BookingStatus | "">("");
const filterDate   = ref("");
const filterTour   = ref("");
const allTours     = ref<Tour[]>([]);

// Per-row action state
const actionState  = ref<Record<string, "idle" | "submitting" | "error">>({});
const actionError  = ref<Record<string, string>>({});
const cancelReason = ref<Record<string, string>>({});

const canView     = computed(() => hasPermission(session.value, "tours-operator.booking:view"));
const canCancel   = computed(() => hasPermission(session.value, "tours-operator.booking:cancel"));
const canComplete = computed(() => hasPermission(session.value, "tours-operator.booking:complete"));
const canViewTours = computed(() => hasPermission(session.value, "tours-operator.tour:view"));

function tourName(tourId: string): string {
  const tour = toursById.value[tourId];
  return tour?.nameEn ?? tour?.slug ?? tourId.slice(0, 8);
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

function errorText(err: unknown): string {
  if (err instanceof ToursApiError) {
    if (err.status === 401) return "Session expired. Please sign in again.";
    if (err.status === 403) return "You don't have permission for this action.";
    if (err.errorCode === "slot_fully_booked")   return "That slot is now fully booked.";
    if (err.errorCode === "already_confirmed")   return "Booking is already confirmed.";
    if (err.errorCode === "already_cancelled")   return "Booking is already cancelled.";
    if (err.errorCode === "invalid_transition")  return "That status change isn't allowed from here.";
    if (err.status === 404) return "Booking not found.";
    return `Request failed (${err.errorCode}).`;
  }
  return "Could not reach the server.";
}

async function load() {
  if (!session.value || !canView.value) return;
  listState.value = "loading";
  listError.value = "";
  const token = session.value.token;
  try {
    const params: Parameters<typeof listBookings>[1] = { page: page.value, size: PAGE_SIZE };
    if (filterStatus.value) params.status = filterStatus.value;
    if (filterDate.value)   params.date   = filterDate.value;
    if (filterTour.value)   params.tourId = filterTour.value;

    const result = await listBookings(token, params);
    bookings.value = result;
    hasNext.value  = result.length === PAGE_SIZE;
    listState.value = "loaded";
  } catch (err) {
    handleApiError(err);
    listState.value = "error";
    listError.value = errorText(err);
  }
}

async function submitCancel(b: Booking) {
  if (!session.value) return;
  const reason = (cancelReason.value[b.id] ?? "").trim();
  if (!reason) {
    actionState.value[b.id] = "error";
    actionError.value[b.id] = "A cancellation reason is required.";
    return;
  }
  if (!window.confirm(`Cancel booking ${b.reference} for ${b.customer.fullName}? This cannot be undone.`)) return;
  actionState.value[b.id] = "submitting";
  actionError.value[b.id] = "";
  try {
    const updated = await cancelBooking(session.value.token, b.id, reason);
    bookings.value = bookings.value.map((x) => (x.id === updated.id ? updated : x));
    actionState.value[b.id] = "idle";
  } catch (err) {
    handleApiError(err);
    actionState.value[b.id] = "error";
    actionError.value[b.id] = errorText(err);
  }
}

async function submitComplete(b: Booking) {
  if (!session.value) return;
  if (!window.confirm(`Mark booking ${b.reference} as completed?`)) return;
  actionState.value[b.id] = "submitting";
  actionError.value[b.id] = "";
  try {
    const updated = await completeBooking(session.value.token, b.id);
    bookings.value = bookings.value.map((x) => (x.id === updated.id ? updated : x));
    actionState.value[b.id] = "idle";
  } catch (err) {
    handleApiError(err);
    actionState.value[b.id] = "error";
    actionError.value[b.id] = errorText(err);
  }
}

onMounted(async () => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  // Load tour list for filter dropdown
  if (canViewTours.value) {
    try {
      allTours.value = await listAllStaffTours(session.value.token);
      toursById.value = Object.fromEntries(allTours.value.map((t) => [t.id, t]));
    } catch { /* non-fatal */ }
  }
  void load();
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">

      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">Bookings</h1>
        </div>
      </header>

      <!-- Filters -->
      <div class="mt-6 flex flex-wrap gap-3">
        <select
          v-model="filterStatus"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          @change="page = 0; load()"
        >
          <option value="">All statuses</option>
          <option v-for="s in ['NEW','CONFIRMED','COMPLETED','CANCELLED','EXPIRED']" :key="s" :value="s">
            {{ s }}
          </option>
        </select>

        <select
          v-model="filterTour"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          @change="page = 0; load()"
        >
          <option value="">All tours</option>
          <option v-for="t in allTours" :key="t.id" :value="t.id">{{ t.nameEn ?? t.slug }}</option>
        </select>

        <input
          v-model="filterDate"
          type="date"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          @change="page = 0; load()"
        >

        <button
          type="button"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm font-semibold hover:bg-sts-canvas"
          @click="filterStatus = ''; filterTour = ''; filterDate = ''; page = 0; load()"
        >
          Clear filters
        </button>
      </div>

      <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

      <p v-if="!canView" class="mt-6 text-sm text-sts-muted">
        Your account doesn't have permission to view bookings (tours-operator.booking:view).
      </p>
      <p v-else-if="listState === 'loading'" class="mt-6 text-sm text-sts-muted">Loading…</p>
      <p v-else-if="listState === 'loaded' && bookings.length === 0 && page === 0" class="mt-6 text-sm text-sts-muted">
        No bookings match the current filters.
      </p>

      <!-- Bookings table -->
      <div v-else-if="bookings.length > 0" class="mt-6 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface shadow-sm">
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-sts-border bg-sts-canvas/60">
                <th class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Ref</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Customer</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Tour</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Date / Time</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Pax</th>
                <th class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">Total</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Status</th>
                <th class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Actions</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="b in bookings" :key="b.id">
                <tr
                  class="cursor-pointer border-b border-sts-border/50 hover:bg-sts-canvas/50"
                  :class="actionState[b.id] === 'error' ? '' : 'last:border-0'"
                  @click="$router.push(`/bookings/${b.id}`)"
                >
                  <td class="ref px-5 py-3.5 font-mono text-xs text-sts-muted">{{ b.reference }}</td>
                  <td class="px-4 py-3.5">
                    <p class="font-medium">{{ b.customer.fullName }}</p>
                    <p class="phone text-xs text-sts-muted">{{ b.customer.phone }}</p>
                  </td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ tourName(b.tourId) }}</td>
                  <td class="px-4 py-3.5 text-sts-muted">
                    {{ b.tourDate }}<br>
                    <span class="text-xs">{{ b.timeSlot }}</span>
                  </td>
                  <td class="px-4 py-3.5">
                    {{ b.adultsCount + b.childrenCount }}
                    <div v-if="b.unit" class="text-xs text-sts-muted">{{ b.unit.unitCount }} × {{ b.unit.optionLabel }}</div>
                  </td>
                  <td class="money px-4 py-3.5 text-end font-semibold">{{ formatMoney(b.totalPrice) }}</td>
                  <td class="px-4 py-3.5">
                    <span :class="`badge badge-${b.status}`">{{ b.status }}</span>
                  </td>
                  <td class="px-5 py-3.5" @click.stop>
                    <div class="flex flex-wrap gap-2">
                      <WegoButton
                        v-if="canComplete && b.status === 'CONFIRMED'"
                        type="button"
                        variant="secondary"
                        :disabled="actionState[b.id] === 'submitting'"
                        @click="submitComplete(b)"
                      >
                        Complete
                      </WegoButton>
                    </div>
                  </td>
                </tr>
                <!-- Cancel row — only expanded when cancellable -->
                <tr
                  v-if="canCancel && (b.status === 'NEW' || b.status === 'CONFIRMED')"
                  :key="`cancel-${b.id}`"
                  class="border-b border-sts-border/50 last:border-0 bg-sts-canvas/30"
                >
                  <td colspan="8" class="px-5 py-3">
                    <WegoAlert v-if="actionState[b.id] === 'error'" variant="danger" class="mb-2">
                      {{ actionError[b.id] }}
                    </WegoAlert>
                    <div class="flex flex-wrap items-end gap-2">
                      <WegoInput
                        :id="`cancel-${b.id}`"
                        :model-value="cancelReason[b.id] ?? ''"
                        label="Cancellation reason"
                        class="min-w-[14rem] flex-1"
                        @update:model-value="(v) => (cancelReason[b.id] = v)"
                      />
                      <WegoButton
                        type="button"
                        variant="secondary"
                        :disabled="actionState[b.id] === 'submitting'"
                        @click="submitCancel(b)"
                      >
                        Cancel booking
                      </WegoButton>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="listState === 'loaded'" class="mt-5 flex items-center gap-3">
        <WegoButton
          type="button"
          variant="secondary"
          :disabled="page === 0"
          @click="page--; load()"
        >
          Previous
        </WegoButton>
        <span class="text-sm text-sts-muted">Page {{ page + 1 }}</span>
        <WegoButton
          type="button"
          variant="secondary"
          :disabled="!hasNext"
          @click="page++; load()"
        >
          Next
        </WegoButton>
      </div>

    </div>
  </main>
</template>
