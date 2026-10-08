<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../../composables/useAuthSession";
import {
  cancelBooking,
  completeBooking,
  listBookings,
  listAllStaffTours,
  ToursApiError,
  type Booking,
  type BookingStatus,
  type Tour,
  PAGE_SIZE,
} from "../../composables/useToursApi";
import { useErpLocale } from "../../composables/useErpLocale";
import OfficePaymentBadge from "../../components/OfficePaymentBadge.vue";
import CalendarDateField from "../../components/CalendarDateField.vue";
import { bookingErrorMessage, type ErpMessageDescriptor } from "../../utils/bookingMessages";

const { t, count, money, dateLabel } = useErpLocale();
useHead(() => ({ title: `${t("nav.bookings")} · Safari Tours Sharm` }));
const statuses: BookingStatus[] = ["NEW", "CONFIRMED", "COMPLETED", "CANCELLED", "EXPIRED"];

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const bookings   = ref<Booking[]>([]);
const toursById  = ref<Record<string, Tour>>({});
const listState  = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError  = ref<ErpMessageDescriptor | null>(null);
const page       = ref(0);
const hasNext    = ref(false);

// Filters
const filterStatus = ref<BookingStatus | "">("");
const filterDate   = ref("");
const filterTour   = ref("");
const allTours     = ref<Tour[]>([]);

// Per-row action state
const actionState  = ref<Record<string, "idle" | "submitting" | "error">>({});
const actionError  = ref<Record<string, ErpMessageDescriptor | null>>({});
const cancelReason = ref<Record<string, string>>({});

const canView     = computed(() => hasPermission(session.value, "tours-operator.booking:view"));
const canCancel   = computed(() => hasPermission(session.value, "tours-operator.booking:cancel"));
const canComplete = computed(() => hasPermission(session.value, "tours-operator.booking:complete"));
const canCreateOffice = computed(() => hasPermission(session.value, "tours-operator.booking:create-office"));
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

function messageText(message?: ErpMessageDescriptor | null): string {
  return message ? t(message.key, message.params) : "";
}

async function load() {
  if (!session.value || !canView.value) return;
  listState.value = "loading";
  listError.value = null;
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
    listError.value = bookingErrorMessage(err);
  }
}

async function submitCancel(b: Booking) {
  if (!session.value) return;
  const reason = (cancelReason.value[b.id] ?? "").trim();
  if (!reason) {
    actionState.value[b.id] = "error";
    actionError.value[b.id] = { key: "bookings.reasonRequired" };
    return;
  }
  if (!window.confirm(t("bookings.confirmCancel", { reference: b.reference, customer: b.customer.fullName }))) return;
  actionState.value[b.id] = "submitting";
  actionError.value[b.id] = null;
  try {
    const updated = await cancelBooking(session.value.token, b.id, reason);
    bookings.value = bookings.value.map((x) => (x.id === updated.id ? updated : x));
    actionState.value[b.id] = "idle";
  } catch (err) {
    handleApiError(err);
    actionState.value[b.id] = "error";
    actionError.value[b.id] = bookingErrorMessage(err);
  }
}

async function submitComplete(b: Booking) {
  if (!session.value) return;
  if (!window.confirm(t("bookings.confirmComplete", { reference: b.reference }))) return;
  actionState.value[b.id] = "submitting";
  actionError.value[b.id] = null;
  try {
    const updated = await completeBooking(session.value.token, b.id);
    bookings.value = bookings.value.map((x) => (x.id === updated.id ? updated : x));
    actionState.value[b.id] = "idle";
  } catch (err) {
    handleApiError(err);
    actionState.value[b.id] = "error";
    actionError.value[b.id] = bookingErrorMessage(err);
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
  <main class="px-4 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">

      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ t('nav.bookings') }}</h1>
        </div>
        <NuxtLink v-if="canCreateOffice" to="/bookings/new" class="rounded-xl bg-sts-ocean px-4 py-2 text-sm font-semibold text-white">{{ t('office.new.cta') }}</NuxtLink>
      </header>

      <!-- Filters -->
      <div class="mt-6 flex flex-wrap gap-3">
        <select
          v-model="filterStatus"
          :aria-label="t('bookings.filterStatus')"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          @change="page = 0; load()"
        >
          <option value="">{{ t('bookings.allStatuses') }}</option>
          <option v-for="s in statuses" :key="s" :value="s">
            {{ t(`status.${s}`) }}
          </option>
        </select>

        <select
          v-model="filterTour"
          :aria-label="t('bookings.filterTour')"
          class="max-w-full rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          @change="page = 0; load()"
        >
          <option value="">{{ t('bookings.allTours') }}</option>
          <option v-for="tour in allTours" :key="tour.id" :value="tour.id">{{ tour.nameEn ?? tour.slug }}</option>
        </select>

        <CalendarDateField
          id="booking-filter-date"
          v-model="filterDate"
          :label="t('bookings.filterDate')"
          class="w-full sm:w-80"
          @update:model-value="(value) => { if (value) { page = 0; load(); } }"
          @cleared="page = 0; load()"
        />

        <button
          type="button"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm font-semibold hover:bg-sts-canvas"
          @click="filterStatus = ''; filterTour = ''; filterDate = ''; page = 0; load()"
        >
          {{ t('bookings.clearFilters') }}
        </button>
      </div>

      <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ messageText(listError) }}</WegoAlert>

      <p v-if="!canView" class="mt-6 text-sm text-sts-muted">
        {{ t('bookings.noPermission') }}
      </p>
      <p v-else-if="listState === 'loading'" class="mt-6 text-sm text-sts-muted">{{ t('common.loading') }}</p>
      <p v-else-if="listState === 'loaded' && bookings.length === 0 && page === 0" class="mt-6 text-sm text-sts-muted">
        {{ t('bookings.empty') }}
      </p>

      <!-- Bookings table -->
      <div v-else-if="bookings.length > 0" class="mt-6 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface shadow-sm">
        <div class="overflow-x-auto" tabindex="0" role="region" :aria-label="t('nav.bookings')">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-sts-border bg-sts-canvas/60">
                <th class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.ref') }}</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.customer') }}</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.tour') }}</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('bookings.dateTime') }}</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.pax') }}</th>
                <th class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">{{ t('common.total') }}</th>
                <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.status') }}</th>
                <th class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="b in bookings" :key="b.id">
                <tr
                  class="border-b border-sts-border/50 hover:bg-sts-canvas/50"
                  :class="actionState[b.id] === 'error' ? '' : 'last:border-0'"
                >
                  <td class="ref px-5 py-3.5 font-mono text-xs text-sts-muted">
                    <NuxtLink :to="`/bookings/${b.id}`" class="font-semibold text-sts-ocean underline underline-offset-4">{{ b.reference }}</NuxtLink>
                  </td>
                  <td class="px-4 py-3.5">
                    <p class="font-medium">{{ b.customer.fullName }}</p>
                    <p class="phone text-xs text-sts-muted">{{ b.customer.phone }}</p>
                  </td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ tourName(b.tourId) }}</td>
                  <td class="px-4 py-3.5 text-sts-muted">
                    {{ dateLabel(b.tourDate) }}<br>
                    <span class="text-xs">{{ t(`slot.${b.timeSlot}`) }}</span>
                  </td>
                  <td class="px-4 py-3.5">
                    {{ count(b.adultsCount + b.childrenCount) }}
                    <div v-if="b.unit" class="text-xs text-sts-muted">{{ count(b.unit.unitCount) }} × {{ b.unit.optionLabel }}</div>
                  </td>
                  <td class="money px-4 py-3.5 text-end font-semibold">{{ money(b.totalPrice) }}</td>
                  <td class="px-4 py-3.5">
                    <span :class="`badge badge-${b.status}`">{{ t(`status.${b.status}`) }}</span>
                    <OfficePaymentBadge v-if="b.channel === 'OFFICE'" :booking="b" class="mt-1 block" />
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
                        {{ t('bookings.complete') }}
                      </WegoButton>
                    </div>
                  </td>
                </tr>
                <tr v-if="actionState[b.id] === 'error'" class="border-b border-sts-border/50">
                  <td colspan="8" class="px-5 py-3">
                    <WegoAlert variant="danger">{{ messageText(actionError[b.id]) }}</WegoAlert>
                  </td>
                </tr>
                <!-- Cancel row — only expanded when cancellable -->
                <tr
                  v-if="canCancel && (b.status === 'NEW' || b.status === 'CONFIRMED')"
                  :key="`cancel-${b.id}`"
                  class="border-b border-sts-border/50 last:border-0 bg-sts-canvas/30"
                >
                  <td colspan="8" class="px-5 py-3">
                    <div class="flex flex-wrap items-end gap-2">
                      <WegoInput
                        :id="`cancel-${b.id}`"
                        :model-value="cancelReason[b.id] ?? ''"
                        :label="t('bookings.cancelReason')"
                        class="min-w-[14rem] flex-1"
                        @update:model-value="(v) => (cancelReason[b.id] = v)"
                      />
                      <WegoButton
                        type="button"
                        variant="secondary"
                        :disabled="actionState[b.id] === 'submitting'"
                        @click="submitCancel(b)"
                      >
                        {{ t('bookings.cancel') }}
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
      <div v-if="listState === 'loaded'" class="mt-5 flex flex-wrap items-center gap-3">
        <WegoButton
          type="button"
          variant="secondary"
          :disabled="page === 0"
          @click="page--; load()"
        >
          {{ t('common.previous') }}
        </WegoButton>
        <span class="text-sm text-sts-muted">{{ t('common.page', { page: count(page + 1) }) }}</span>
        <WegoButton
          type="button"
          variant="secondary"
          :disabled="!hasNext"
          @click="page++; load()"
        >
          {{ t('common.next') }}
        </WegoButton>
      </div>

    </div>
  </main>
</template>
