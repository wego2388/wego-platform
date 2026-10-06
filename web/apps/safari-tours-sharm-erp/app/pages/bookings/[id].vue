<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../../composables/useAuthSession";
import {
  cancelBooking,
  completeBooking,
  getBooking,
  getBookingHistory,
  getPaymentHistory,
  ToursApiError,
  type Booking,
  type BookingHistoryEntry,
  type PaymentHistoryEntry,
} from "../../composables/useToursApi";
import { mergeTimeline } from "../../composables/useBookingTimeline";
import { whatsappLink } from "../../composables/useWhatsApp";
import { useErpLocale } from "../../composables/useErpLocale";
import OfficePaymentBadge from "../../components/OfficePaymentBadge.vue";
import OfficePaymentsPanel from "../../components/OfficePaymentsPanel.vue";
import { bookingErrorMessage, type ErpMessageDescriptor } from "../../utils/bookingMessages";
import { docMessage } from "../../utils/documentMessages";
import { documentPath } from "../../utils/documentFormat";

const { t, locale, direction, count, money, dateLabel, instantLabel } = useErpLocale();
useHead(() => ({ title: `${t("booking.title")} · Safari Tours Sharm` }));

const route  = useRoute();
const router = useRouter();

const session        = ref<AuthSession | null>(null);
const booking        = ref<Booking | null>(null);
const loadState      = ref<"idle" | "loading" | "loaded" | "error">("idle");
const loadError      = ref<ErpMessageDescriptor | null>(null);
const actionState    = ref<"idle" | "submitting" | "success" | "error">("idle");
const actionError    = ref<ErpMessageDescriptor | null>(null);
const cancelReason   = ref("");
const showCancelForm = ref(false);
const bookingHistory = ref<BookingHistoryEntry[]>([]);
const paymentHistory = ref<PaymentHistoryEntry[]>([]);
const timeline       = computed(() => mergeTimeline(bookingHistory.value, paymentHistory.value, locale.value));
const timelineError  = ref(false);

const canCancel   = computed(() => hasPermission(session.value, "tours-operator.booking:cancel"));
const canComplete = computed(() => hasPermission(session.value, "tours-operator.booking:complete"));
const canViewPayments = computed(() => hasPermission(session.value, "tours-operator.payment:view"));
const canPrintDocs = computed(() => hasPermission(session.value, "tours-operator.document:print"));
/** A draft (awaiting online payment) or expired booking is never a voucher. */
const canPrintVoucher = computed(() => canPrintDocs.value && !!booking.value && ["CONFIRMED", "COMPLETED", "CANCELLED"].includes(booking.value.status));
const canPrintCancellation = computed(() => canPrintDocs.value && booking.value?.channel === "OFFICE" && booking.value.status === "CANCELLED" && !!booking.value.officePayment?.cashToReturn);

const bookingId = computed(() => String(route.params.id));
const whatsappUrl = computed(() => (booking.value ? whatsappLink(booking.value) : null));

function messageText(message: ErpMessageDescriptor | null): string {
  return message ? t(message.key, message.params) : "";
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  loadState.value = "loading";
  loadError.value = null;
  try {
    booking.value   = await getBooking(session.value.token, bookingId.value);
    loadState.value = "loaded";
    void loadTimeline();
  } catch (err) {
    handleApiError(err);
    loadError.value = bookingErrorMessage(err);
    loadState.value = "error";
  }
}

/** History is secondary: a failure here never hides the booking itself. */
async function loadTimeline() {
  if (!session.value) return;
  timelineError.value = false;
  try {
    const token = session.value.token;
    const [bookingEntries, paymentEntries] = await Promise.all([
      getBookingHistory(token, bookingId.value),
      canViewPayments.value ? getPaymentHistory(token, bookingId.value) : Promise.resolve([]),
    ]);
    bookingHistory.value = bookingEntries;
    paymentHistory.value = paymentEntries;
  } catch (err) {
    handleApiError(err);
    timelineError.value = true;
  }
}

async function doCancel() {
  if (!session.value || !booking.value) return;
  if (!cancelReason.value.trim()) {
    actionError.value = { key: "bookings.reasonRequired" };
    actionState.value = "error";
    return;
  }
  actionState.value = "submitting";
  actionError.value = null;
  try {
    booking.value        = await cancelBooking(session.value.token, booking.value.id, cancelReason.value.trim());
    actionState.value    = "success";
    showCancelForm.value = false;
    cancelReason.value   = "";
    void loadTimeline();
  } catch (err) {
    handleApiError(err);
    actionError.value = bookingErrorMessage(err);
    actionState.value = "error";
  }
}

async function doComplete() {
  if (!session.value || !booking.value) return;
  actionState.value = "submitting";
  actionError.value = null;
  try {
    booking.value     = await completeBooking(session.value.token, booking.value.id);
    actionState.value = "success";
    void loadTimeline();
  } catch (err) {
    handleApiError(err);
    actionError.value = bookingErrorMessage(err);
    actionState.value = "error";
  }
}


onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  void load();
});
</script>

<template>
  <main class="px-4 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <NuxtLink to="/bookings" class="text-sm text-sts-muted hover:text-sts-ocean"><span aria-hidden="true">{{ direction === 'rtl' ? '→' : '←' }}</span> {{ t('nav.bookings') }}</NuxtLink>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">
            {{ t('booking.heading') }}
            <span v-if="booking" class="ref text-sts-ocean">{{ booking.reference }}</span>
            <span v-else class="text-sts-muted">…</span>
          </h1>
        </div>
      </header>

      <!-- Load error -->
      <WegoAlert v-if="loadState === 'error'" variant="danger" class="mt-6">{{ messageText(loadError) }}</WegoAlert>

      <!-- Loading -->
      <p v-else-if="loadState === 'loading'" class="mt-6 text-sm text-sts-muted">{{ t('booking.loading') }}</p>

      <!-- Booking detail -->
      <template v-else-if="booking">

        <!-- Action feedback -->
        <WegoAlert v-if="actionState === 'success'" variant="success" class="mt-6">
          {{ t('booking.updated') }}
        </WegoAlert>
        <WegoAlert v-if="actionState === 'error'" variant="danger" class="mt-6">
          {{ messageText(actionError) }}
        </WegoAlert>

        <!-- Status + action buttons -->
        <div class="mt-6 flex flex-wrap items-center gap-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
          <div class="flex items-center gap-3">
            <span class="text-sm font-semibold text-sts-muted">{{ t('common.status') }}</span>
            <span :class="`badge badge-${booking.status}`">{{ t(`status.${booking.status}`) }}</span>
            <OfficePaymentBadge v-if="booking.channel === 'OFFICE'" :booking="booking" />
          </div>
          <div class="ms-auto flex flex-wrap gap-2">
            <a
              v-if="whatsappUrl"
              :href="whatsappUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center rounded-xl border border-sts-border px-3 py-1.5 text-sm font-semibold text-sts-ocean hover:bg-sts-canvas"
            >
              {{ t('booking.whatsapp') }}
            </a>
            <NuxtLink
              v-if="canPrintVoucher"
              :to="documentPath('voucher', booking.id)"
              class="inline-flex items-center rounded-xl border border-sts-border px-3 py-1.5 text-sm font-semibold text-sts-ocean hover:bg-sts-canvas"
            >
              {{ docMessage(locale, 'doc.ui.printVoucher') }}
            </NuxtLink>
            <NuxtLink
              v-if="canPrintCancellation"
              :to="documentPath('cancellation', booking.id)"
              class="inline-flex items-center rounded-xl border border-sts-border px-3 py-1.5 text-sm font-semibold text-sts-ocean hover:bg-sts-canvas"
            >
              {{ docMessage(locale, 'doc.ui.printCancellation') }}
            </NuxtLink>
            <WegoButton
              v-if="booking.status === 'CONFIRMED' && canComplete"
              type="button" variant="primary" size="sm"
              :disabled="actionState === 'submitting'"
              @click="doComplete"
            >
              {{ actionState === 'submitting' ? t('booking.completing') : t('booking.markCompleted') }}
            </WegoButton>
            <WegoButton
              v-if="(booking.status === 'NEW' || booking.status === 'CONFIRMED') && canCancel"
              type="button" variant="secondary" size="sm"
              class="border-sts-danger text-sts-danger hover:bg-sts-danger-soft"
              :disabled="actionState === 'submitting'"
              @click="showCancelForm = !showCancelForm"
            >
              {{ t('bookings.cancel') }}
            </WegoButton>
          </div>
        </div>

        <!-- Cancel form -->
        <div v-if="showCancelForm" class="mt-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
          <label for="cancel-reason" class="block text-sm font-semibold mb-2">
            {{ t('bookings.cancelReason') }}
          </label>
          <textarea
            id="cancel-reason"
            v-model="cancelReason"
            rows="3"
            required
            :placeholder="t('booking.reasonHelp')"
            class="w-full rounded-xl border border-sts-border bg-sts-canvas px-4 py-2.5 text-sm focus:outline-sts-gold resize-none"
          />
          <div class="mt-3 flex gap-2">
            <WegoButton
              type="button" variant="secondary" size="sm"
              class="border-sts-danger text-sts-danger hover:bg-sts-danger-soft"
              :disabled="actionState === 'submitting' || !cancelReason.trim()"
              @click="doCancel"
            >
              {{ actionState === 'submitting' ? t('booking.cancelling') : t('booking.confirmCancellation') }}
            </WegoButton>
            <WegoButton
              type="button" variant="secondary" size="sm"
              @click="showCancelForm = false; cancelReason = ''"
            >
              {{ t('booking.dismiss') }}
            </WegoButton>
          </div>
        </div>

        <!-- Details grid -->
        <div class="mt-6 grid gap-4 sm:grid-cols-2">

          <!-- Booking info -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">{{ t('booking.details') }}</h2>
            <dl class="detail-fields space-y-2 text-sm">
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.reference') }}</dt>
                <dd class="ref font-mono font-semibold text-sts-ocean">{{ booking.reference }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.tourDate') }}</dt>
                <dd>{{ dateLabel(booking.tourDate) }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.timeSlot') }}</dt>
                <dd>{{ t(`slot.${booking.timeSlot}`) }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.adults') }}</dt>
                <dd class="tabular-nums">{{ count(booking.adultsCount) }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.children') }}</dt>
                <dd class="tabular-nums">{{ count(booking.childrenCount) }}</dd>
              </div>
              <div v-if="booking.unit" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.bookedAs') }}</dt>
                <dd class="tabular-nums">{{ t('booking.unitPrice', { count: count(booking.unit.unitCount), label: booking.unit.optionLabel, price: money(booking.unit.unitPrice) }) }}</dd>
              </div>
              <div class="flex justify-between gap-4 border-t border-sts-border pt-2">
                <dt class="text-sts-muted shrink-0">{{ t('common.total') }}</dt>
                <dd class="money font-bold text-base">{{ money(booking.totalPrice) }}</dd>
              </div>
              <div class="flex justify-between gap-4 border-t border-sts-border pt-2">
                <dt class="text-sts-muted shrink-0">{{ t('booking.created') }}</dt>
                <dd>{{ instantLabel(booking.createdAt) }}</dd>
              </div>
              <div v-if="booking.confirmedAt" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.confirmed') }}</dt>
                <dd>{{ instantLabel(booking.confirmedAt) }}</dd>
              </div>
              <div v-if="booking.completedAt" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.completed') }}</dt>
                <dd>{{ instantLabel(booking.completedAt) }}</dd>
              </div>
              <div v-if="booking.cancelledAt" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.cancelled') }}</dt>
                <dd>{{ instantLabel(booking.cancelledAt) }}</dd>
              </div>
              <div v-if="booking.cancellationReason" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.cancelReason') }}</dt>
                <dd class="text-end">{{ booking.cancellationReason }}</dd>
              </div>
            </dl>
          </div>

          <!-- Customer info -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">{{ t('common.customer') }}</h2>
            <dl class="detail-fields space-y-2 text-sm">
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.name') }}</dt>
                <dd class="font-medium text-end">{{ booking.customer.fullName }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.phone') }}</dt>
                <dd>
                  <a
:href="`tel:${booking.customer.phone}`"
                    class="phone text-sts-ocean underline underline-offset-2">
                    {{ booking.customer.phone }}
                  </a>
                </dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.nationality') }}</dt>
                <dd>{{ booking.customer.nationality }}</dd>
              </div>
              <div v-if="booking.customer.email" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.email') }}</dt>
                <dd class="break-all text-end">{{ booking.customer.email }}</dd>
              </div>
              <div class="flex justify-between gap-4 border-t border-sts-border pt-2">
                <dt class="text-sts-muted shrink-0">{{ t('common.hotel') }}</dt>
                <dd class="text-end">{{ booking.hotelName }}</dd>
              </div>
              <div v-if="booking.hotelRoom" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.room') }}</dt>
                <dd>{{ booking.hotelRoom }}</dd>
              </div>
              <div v-if="booking.specialRequests" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.requests') }}</dt>
                <dd class="text-end">{{ booking.specialRequests }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t('booking.locale') }}</dt>
                <dd>{{ booking.locale }}</dd>
              </div>
            </dl>
          </div>

        </div>

        <OfficePaymentsPanel v-if="booking.channel === 'OFFICE' && session" :booking="booking" :session="session" @changed="load" />

        <!-- History -->
        <section class="mt-6 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm" aria-labelledby="history-heading">
          <h2 id="history-heading" class="mb-1 text-sm font-semibold text-sts-muted uppercase tracking-wide">{{ t('booking.history') }}</h2>
          <p v-if="!canViewPayments" class="mb-3 text-xs text-sts-muted">{{ t('booking.paymentPermission') }}</p>
          <WegoAlert v-if="timelineError" variant="danger" class="mt-2">{{ t('booking.historyFailed') }}</WegoAlert>
          <p v-else-if="timeline.length === 0" class="text-sm text-sts-muted">{{ t('booking.noHistory') }}</p>
          <ol v-else class="mt-3 space-y-3">
            <li v-for="item in timeline" :key="item.key" class="flex gap-3 text-sm">
              <span
                class="mt-1.5 h-2 w-2 shrink-0 rounded-full"
                :class="item.source === 'payment' ? 'bg-sts-gold' : 'bg-sts-ocean'"
                aria-hidden="true"
              />
              <div class="min-w-0">
                <p class="font-medium">
                  {{ item.title }}
                  <span v-if="!item.recorded" class="ms-1 text-xs font-normal text-sts-muted">{{ t('booking.reconstructed') }}</span>
                </p>
                <p class="text-xs text-sts-muted">
                  <time :datetime="item.occurredAt">{{ instantLabel(item.occurredAt) }}</time>
                  <span v-if="item.actor"> · <bdi>{{ item.actor }}</bdi></span>
                </p>
                <p v-if="item.detail" class="mt-0.5 break-words text-xs">{{ item.detail }}</p>
              </div>
            </li>
          </ol>
        </section>
      </template>

    </div>
  </main>
</template>

<style scoped>
.detail-fields dd {
  min-width: 0;
  overflow-wrap: anywhere;
  text-align: end;
}
</style>
