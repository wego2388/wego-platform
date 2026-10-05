<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoPageHeader, WegoPagination, WegoPanel } from "@wego/ui";
import { type AuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  listTravelNotifications,
  resendTravelNotification,
  type NotificationKind,
  type NotificationStatus,
  type TravelNotification,
  TravelMarketplaceApiError,
} from "../composables/useTravelMarketplaceApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Notifications · Sharm To Go" });

// The list never carries a recipient address or a message body: neither is
// stored. A row shows what was sent about which request, and how it went.
const KIND_LABEL: Record<NotificationKind, string> = {
  STAFF_NEW_REQUEST: "Staff alert: new request",
  CUSTOMER_REQUEST_RECEIVED: "Customer: request received",
  CUSTOMER_REQUEST_CONFIRMED: "Customer: request confirmed",
  CUSTOMER_REQUEST_CANCELLED: "Customer: request cancelled",
};

const REASON_LABEL: Record<string, string> = {
  no_customer_email: "The customer gave no email address",
  no_staff_address: "No staff alert address is configured",
  request_state_changed: "The request changed state, so this message was no longer true",
  superseded_by_confirmation: "Replaced by the confirmation email",
  request_missing: "The request no longer exists",
  email_not_configured: "Email delivery is not configured",
};

function statusTone(status: NotificationStatus): "neutral" | "accent" | "success" | "warning" | "danger" {
  if (status === "SENT") return "success";
  if (status === "PENDING") return "accent";
  if (status === "FAILED") return "danger";
  return "warning";
}

const session = ref<AuthSession | null>(null);
const notifications = ref<TravelNotification[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const actionMessage = ref("");
const page = ref(0);
const hasNextPage = ref(false);
const statusFilter = ref<NotificationStatus | "">("");
const resendingId = ref<string | null>(null);

const canManage = () => hasPermission(session.value, "travel-notification:manage");

function errorText(error: unknown): string {
  if (error instanceof TravelMarketplaceApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission to manage notifications.";
    if (error.status === 404) return "That notification no longer exists.";
    if (error.status === 409) return "That message is no longer true for the request's current state, so it was not queued.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadNotifications() {
  if (!session.value) return;
  if (!canManage()) {
    notifications.value = [];
    hasNextPage.value = false;
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const result = await listTravelNotifications(session.value.token, { status: statusFilter.value || undefined, page: page.value });
    notifications.value = result;
    hasNextPage.value = result.length === 50;
    listState.value = "loaded";
  } catch (error) {
    listState.value = "error";
    listError.value = errorText(error);
  }
}

async function resend(notification: TravelNotification) {
  if (!session.value || resendingId.value) return;
  resendingId.value = notification.id;
  actionMessage.value = "";
  listError.value = "";
  try {
    await resendTravelNotification(session.value.token, notification.id);
    actionMessage.value = `Queued again for ${notification.requestReference}. It is sent on the next delivery run.`;
    await loadNotifications();
  } catch (error) {
    listState.value = "error";
    listError.value = errorText(error);
  } finally {
    resendingId.value = null;
  }
}

function nextPage() {
  page.value += 1;
  loadNotifications();
}

function previousPage() {
  if (page.value === 0) return;
  page.value -= 1;
  loadNotifications();
}

function runFilter() {
  page.value = 0;
  loadNotifications();
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(undefined, { year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

function reasonText(code: string): string {
  return REASON_LABEL[code] ?? `Reason code: ${code}`;
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadNotifications();
});
</script>

<template>
  <WegoPageHeader
    eyebrow="Sharm To Go"
    title="Notifications"
    description="Emails about requests: the staff alert and the customer's received, confirmed and cancelled messages."
  />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view notifications.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>
    <WegoAlert v-if="actionMessage" variant="success" class="mt-6">{{ actionMessage }}</WegoAlert>

    <WegoPanel class="mt-8">
      <p v-if="!canManage()" class="text-sm text-wego-muted">
        Your account doesn't have permission to manage notifications (travel-notification:manage).
      </p>
      <template v-else>
        <div>
          <label for="statusFilter" class="block text-sm font-medium text-wego-muted">Status</label>
          <select
            id="statusFilter"
            v-model="statusFilter"
            class="mt-2 rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
            @change="runFilter"
          >
            <option value="">All</option>
            <option value="PENDING">Pending</option>
            <option value="SENT">Sent</option>
            <option value="FAILED">Failed</option>
            <option value="SKIPPED">Skipped</option>
          </select>
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && notifications.length === 0" class="mt-3 text-sm text-wego-muted">
          No notifications{{ statusFilter ? " with this status" : " yet" }}.
        </p>

        <ul v-else-if="notifications.length > 0" class="mt-4 space-y-3">
          <li
            v-for="notification in notifications"
            :key="notification.id"
            class="rounded-wego-control border p-4"
            :class="notification.status === 'FAILED' ? 'border-wego-danger' : 'border-wego-border'"
          >
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <NuxtLink :to="`/requests/${notification.requestId}`" class="reference font-mono text-sm font-semibold text-wego-accent">
                  {{ notification.requestReference }}
                </NuxtLink>
                <p class="mt-1 font-semibold">{{ KIND_LABEL[notification.kind] }}</p>
                <p class="mt-1 text-sm text-wego-muted">
                  Created {{ formatDateTime(notification.createdAt) }}
                  <template v-if="notification.sentAt"> · Sent {{ formatDateTime(notification.sentAt) }}</template>
                  · Attempts {{ notification.attemptCount }}
                  <template v-if="notification.resendCount > 0"> · Resent {{ notification.resendCount }}×</template>
                </p>
                <p v-if="notification.lastError" class="mt-1 text-sm text-wego-muted">{{ reasonText(notification.lastError) }}</p>
              </div>
              <div class="flex items-center gap-3">
                <WegoBadge :tone="statusTone(notification.status)">{{ notification.status }}</WegoBadge>
                <button
                  type="button"
                  class="resend rounded-wego-control border border-wego-border px-3 py-1.5 text-sm font-medium disabled:opacity-50"
                  :disabled="resendingId !== null"
                  @click="resend(notification)"
                >
                  Resend
                </button>
              </div>
            </div>
          </li>
        </ul>

        <WegoPagination class="mt-4" :page="page" :has-next-page="hasNextPage" @previous="previousPage" @next="nextPage" />
      </template>
    </WegoPanel>
  </template>
</template>
