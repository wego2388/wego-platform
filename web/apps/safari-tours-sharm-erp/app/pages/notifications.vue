<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoDialog } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import {
  listNotifications,
  resendNotification,
  ToursApiError,
  type CustomerNotification,
  type NotificationStatus,
} from "../composables/useToursApi";

useHead({ title: "Customer messages · Safari Tours Sharm" });

const router = useRouter();
const session = ref<AuthSession | null>(null);
const notifications = ref<CustomerNotification[]>([]);
const state = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error = ref("");
const statusFilter = ref<NotificationStatus | "">("");
const rowState = ref<Record<string, "idle" | "submitting" | "done" | "error">>({});
const rowError = ref<Record<string, string>>({});
/** A message the customer already received is resent only after an explicit confirmation. */
const confirmingResend = ref<CustomerNotification | null>(null);

const canView = computed(() => hasPermission(session.value, "tours-operator.booking:view"));
const canResend = computed(() => hasPermission(session.value, "tours-operator.notification:manage"));

const KIND_LABELS: Record<CustomerNotification["kind"], string> = {
  BOOKING_CONFIRMED: "Booking confirmation",
  BOOKING_CANCELLED: "Cancellation notice",
  REVIEW_REQUEST: "Review request",
};

const STATUS_TONES: Record<NotificationStatus, "success" | "danger" | "warning" | "neutral"> = {
  SENT: "success",
  FAILED: "danger",
  PENDING: "warning",
  SKIPPED: "neutral",
};

/** Plain-language reasons for the PII-free codes stored by the dispatcher. */
function reasonText(item: CustomerNotification): string | null {
  switch (item.lastError) {
    case null:
    case undefined:
      return null;
    case "no_customer_email":
      return "No email on the booking — contact the customer on WhatsApp.";
    case "review_link_not_configured":
      return "Review link is not configured yet.";
    case "booking_missing":
      return "The booking no longer exists.";
    case "booking_state_changed":
      return "Not sent: the booking changed since (e.g. cancelled), so this message is no longer true.";
    case "tour_already_past":
      return "Not sent: the tour date had already passed.";
    default:
      return `Delivery error: ${item.lastError}`;
  }
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  state.value = "loading";
  error.value = "";
  try {
    notifications.value = await listNotifications(session.value.token, {
      status: statusFilter.value || undefined,
      size: 200,
    });
    state.value = "loaded";
  } catch (err) {
    handleApiError(err);
    error.value = err instanceof ToursApiError ? err.errorCode : "Failed to load messages.";
    state.value = "error";
  }
}

function requestResend(item: CustomerNotification) {
  if (item.status === "SENT") {
    confirmingResend.value = item;
    return;
  }
  void resend(item);
}

async function resend(item: CustomerNotification) {
  confirmingResend.value = null;
  if (!session.value) return;
  rowState.value[item.id] = "submitting";
  rowError.value[item.id] = "";
  try {
    await resendNotification(session.value.token, item.id);
    rowState.value[item.id] = "done";
    await load();
  } catch (err) {
    handleApiError(err);
    rowState.value[item.id] = "error";
    rowError.value[item.id] =
      err instanceof ToursApiError && err.errorCode === "booking_state_changed"
        ? "The booking changed since, so this message is no longer true and was not queued."
        : "Could not queue the message again.";
  }
}


onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  if (!canView.value) { void router.replace("/"); return; }
  void load();
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">Customer messages</h1>
          <p class="text-sm text-sts-muted">Booking confirmations, cancellations and review requests sent by email.</p>
        </div>
      </header>

      <div class="mt-6 flex flex-wrap items-end gap-3">
        <label class="flex flex-col gap-1 text-xs font-semibold text-sts-muted" for="status-filter">
          Status
          <select
            id="status-filter"
            v-model="statusFilter"
            class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm font-normal text-sts-ink"
            @change="load"
          >
            <option value="">All</option>
            <option value="PENDING">Pending</option>
            <option value="SENT">Sent</option>
            <option value="FAILED">Failed</option>
            <option value="SKIPPED">Skipped</option>
          </select>
        </label>
        <WegoButton type="button" variant="secondary" size="sm" @click="load">Refresh</WegoButton>
      </div>

      <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ error }}</WegoAlert>
      <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted">Loading…</p>
      <p v-else-if="state === 'loaded' && notifications.length === 0" class="mt-6 text-sm text-sts-muted">
        No customer messages yet.
      </p>

      <ul v-else-if="state === 'loaded'" class="mt-6 space-y-3" aria-label="Customer messages">
        <li
          v-for="item in notifications"
          :key="item.id"
          class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <p class="font-semibold">{{ KIND_LABELS[item.kind] }}</p>
                <WegoBadge :tone="STATUS_TONES[item.status]">{{ item.status }}</WegoBadge>
              </div>
              <p class="mt-1 text-sm">
                Booking
                <NuxtLink :to="`/bookings/${item.bookingId}`" class="ref font-mono text-sts-ocean underline underline-offset-2">
                  {{ item.bookingReference }}
                </NuxtLink>
              </p>
              <p class="mt-1 text-xs text-sts-muted">
                <template v-if="item.sentAt">Sent {{ new Date(item.sentAt).toLocaleString() }}</template>
                <template v-else-if="item.status === 'PENDING'">
                  Due {{ new Date(item.availableAt).toLocaleString() }}
                  <span v-if="item.attemptCount > 0"> · attempt {{ item.attemptCount + 1 }}</span>
                </template>
                <template v-else>Created {{ new Date(item.createdAt).toLocaleString() }}</template>
                <span v-if="item.resendCount > 0"> · resent {{ item.resendCount }}×</span>
              </p>
              <p v-if="reasonText(item)" class="mt-1 text-xs text-sts-danger">{{ reasonText(item) }}</p>
            </div>
            <WegoButton
              v-if="canResend && item.status !== 'PENDING'"
              type="button"
              variant="secondary"
              size="sm"
              :disabled="rowState[item.id] === 'submitting'"
              @click="requestResend(item)"
            >
              {{ rowState[item.id] === 'submitting' ? 'Queuing…' : 'Send again' }}
            </WegoButton>
          </div>
          <p v-if="rowState[item.id] === 'error'" class="mt-2 text-xs text-sts-danger">{{ rowError[item.id] }}</p>
        </li>
      </ul>

      <WegoDialog :open="confirmingResend !== null" title="Send this message again?" @close="confirmingResend = null">
        <p class="text-sm text-sts-muted">
          The customer already received this
          {{ confirmingResend ? KIND_LABELS[confirmingResend.kind].toLowerCase() : "message" }}
          for booking {{ confirmingResend?.bookingReference }}. Send it again?
        </p>
        <template #actions>
          <WegoButton type="button" variant="secondary" @click="confirmingResend = null">Cancel</WegoButton>
          <WegoButton type="button" @click="confirmingResend && resend(confirmingResend)">Send again</WegoButton>
        </template>
      </WegoDialog>
    </div>
  </main>
</template>
