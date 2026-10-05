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
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import { notificationReasonMessage, notificationResendErrorMessage, operationsErrorMessage } from "../utils/operationsMessages";
import ErpLanguageSwitch from "../components/ErpLanguageSwitch.vue";

const { t, count, instantLabel } = useErpLocale();
useHead(() => ({ title: `${t("messages.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const notifications = ref<CustomerNotification[]>([]);
const state = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error = ref<ErpMessageDescriptor | null>(null);
const statusFilter = ref<NotificationStatus | "">("");
const rowState = ref<Record<string, "idle" | "submitting" | "done" | "error">>({});
const rowError = ref<Record<string, ErpMessageDescriptor | null>>({});
let loadVersion = 0;
/** A message the customer already received is resent only after an explicit confirmation. */
const confirmingResend = ref<CustomerNotification | null>(null);

const canView = computed(() => hasPermission(session.value, "tours-operator.booking:view"));
const canResend = computed(() => hasPermission(session.value, "tours-operator.notification:manage"));

const STATUS_TONES: Record<NotificationStatus, "success" | "danger" | "warning" | "neutral"> = {
  SENT: "success",
  FAILED: "danger",
  PENDING: "warning",
  SKIPPED: "neutral",
};

function reasonText(item: CustomerNotification): string | null {
  const message = notificationReasonMessage(item.lastError);
  return message ? t(message.key, message.params) : null;
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  const version = ++loadVersion;
  state.value = "loading";
  error.value = null;
  try {
    const result = await listNotifications(session.value.token, {
      status: statusFilter.value || undefined,
      size: 200,
    });
    if (version !== loadVersion) return;
    notifications.value = result;
    state.value = "loaded";
  } catch (err) {
    handleApiError(err);
    if (version !== loadVersion) return;
    error.value = operationsErrorMessage(err);
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
  rowError.value[item.id] = null;
  try {
    await resendNotification(session.value.token, item.id);
    rowState.value[item.id] = "done";
    await load();
  } catch (err) {
    handleApiError(err);
    rowState.value[item.id] = "error";
    rowError.value[item.id] = notificationResendErrorMessage(err);
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
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ t("messages.title") }}</h1>
          <p class="text-sm text-sts-muted">{{ t("messages.subtitle") }}</p>
        </div>
      </header>

      <div class="mt-6 flex flex-wrap items-end gap-3">
        <label class="flex flex-col gap-1 text-xs font-semibold text-sts-muted" for="status-filter">
          {{ t("common.status") }}
          <select
            id="status-filter"
            v-model="statusFilter"
            class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm font-normal text-sts-ink"
            @change="load"
          >
            <option value="">{{ t("messages.all") }}</option>
            <option v-for="status in ['PENDING', 'SENT', 'FAILED', 'SKIPPED'] as const" :key="status" :value="status">{{ t(`messages.${status}`) }}</option>
          </select>
        </label>
        <WegoButton type="button" variant="secondary" size="sm" @click="load">{{ t("common.refresh") }}</WegoButton>
      </div>
      <p class="mt-3 text-xs text-sts-muted">{{ t("messages.scope") }}</p>

      <WegoAlert v-if="state === 'error' && error" variant="danger" class="mt-6 break-words">{{ t(error.key, error.params) }}</WegoAlert>
      <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted" role="status">{{ t("common.loading") }}</p>
      <p v-else-if="state === 'loaded' && notifications.length === 0" class="mt-6 text-sm text-sts-muted">
        {{ t("messages.empty") }}
      </p>

      <ul v-else-if="state === 'loaded'" class="mt-6 space-y-3" :aria-label="t('messages.title')">
        <li
          v-for="item in notifications"
          :key="item.id"
          class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <p class="font-semibold">{{ t(`messages.${item.kind}`) }}</p>
                <WegoBadge :tone="STATUS_TONES[item.status]">{{ t(`messages.${item.status}`) }}</WegoBadge>
              </div>
              <p class="mt-1 text-sm">
                {{ t("messages.booking") }}
                <NuxtLink :to="`/bookings/${item.bookingId}`" class="ref font-mono text-sts-ocean underline underline-offset-2">
                  {{ item.bookingReference }}
                </NuxtLink>
              </p>
              <p class="mt-1 text-xs text-sts-muted">
                <template v-if="item.sentAt">{{ t("messages.sentAt", { date: instantLabel(item.sentAt) }) }}</template>
                <template v-else-if="item.status === 'PENDING'">
                  {{ t("messages.dueAt", { date: instantLabel(item.availableAt) }) }}
                  <span v-if="item.attemptCount > 0"> · {{ t("messages.attempt", { count: count(item.attemptCount + 1) }) }}</span>
                </template>
                <template v-else>{{ t("messages.createdAt", { date: instantLabel(item.createdAt) }) }}</template>
                <span v-if="item.resendCount > 0"> · {{ t("messages.resent", { count: count(item.resendCount) }) }}</span>
              </p>
              <p v-if="reasonText(item)" class="mt-1 break-all text-xs text-sts-danger">{{ reasonText(item) }}</p>
            </div>
            <WegoButton
              v-if="canResend && item.status !== 'PENDING'"
              type="button"
              variant="secondary"
              size="sm"
              :disabled="rowState[item.id] === 'submitting'"
              @click="requestResend(item)"
            >
              {{ t(rowState[item.id] === 'submitting' ? 'messages.queuing' : 'messages.sendAgain') }}
            </WegoButton>
          </div>
          <p v-if="rowState[item.id] === 'error' && rowError[item.id]" class="mt-2 text-xs text-sts-danger" role="alert">{{ t(rowError[item.id]!.key, rowError[item.id]!.params) }}</p>
        </li>
      </ul>

      <WegoDialog :open="confirmingResend !== null" :title="t('messages.confirmTitle')" @close="confirmingResend = null">
        <ErpLanguageSwitch class="mb-4" />
        <p class="text-sm text-sts-muted">
          {{ t("messages.confirmBody", { kind: confirmingResend ? t(`messages.${confirmingResend.kind}`) : "", reference: confirmingResend?.bookingReference ?? "" }) }}
        </p>
        <template #actions>
          <WegoButton type="button" variant="secondary" @click="confirmingResend = null">{{ t("common.cancel") }}</WegoButton>
          <WegoButton type="button" @click="confirmingResend && resend(confirmingResend)">{{ t("messages.sendAgain") }}</WegoButton>
        </template>
      </WegoDialog>
    </div>
  </main>
</template>
