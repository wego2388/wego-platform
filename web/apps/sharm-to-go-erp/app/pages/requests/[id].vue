<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoDialog, WegoPageHeader, WegoPanel, WegoSelect, WegoTextarea } from "@wego/ui";
import { type AuthSession, hasPermission, readAuthSession } from "../../composables/useAuthSession";
import {
  cancelTravelRequest,
  completeTravelRequest,
  confirmTravelRequest,
  getTravelRequest,
  getTravelRequestAudit,
  startTravelRequestReview,
  TravelMarketplaceApiError,
  type TravelRequest,
  type TravelRequestAuditEvent,
  type TravelRequestCancelReason,
  type TravelRequestStatus,
} from "../../composables/useTravelMarketplaceApi";

definePageMeta({ layout: "app-shell" });

const route = useRoute();
const requestId = String(route.params.id);

useHead({ title: "Request · Sharm To Go" });

function statusTone(status: TravelRequestStatus): "neutral" | "accent" | "success" | "warning" | "danger" {
  if (status === "NEW") return "warning";
  if (status === "IN_REVIEW") return "accent";
  if (status === "CONFIRMED") return "success";
  if (status === "COMPLETED") return "neutral";
  return "danger";
}

const session = ref<AuthSession | null>(null);
const request = ref<TravelRequest | null>(null);
const auditEvents = ref<TravelRequestAuditEvent[]>([]);
const state = ref<"loading" | "loaded" | "not-found" | "error">("loading");
const stateError = ref("");
const actionState = ref<"idle" | "submitting">("idle");
const actionError = ref("");

const canView = () => hasPermission(session.value, "travel-request:view");
const canReview = () => hasPermission(session.value, "travel-request:review");
const canConfirm = () => hasPermission(session.value, "travel-request:confirm");
const canCancel = () => hasPermission(session.value, "travel-request:cancel");
const canComplete = () => hasPermission(session.value, "travel-request:complete");

function errorText(error: unknown): string {
  if (error instanceof TravelMarketplaceApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this action.";
    if (error.errorCode === "invalid_transition") return "This request is no longer in a state that allows that action.";
    if (error.errorCode === "already_terminal") return "This request is already in a final state and can't be changed further.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function load() {
  if (!session.value || !canView()) return;
  state.value = "loading";
  stateError.value = "";
  try {
    const [loadedRequest, loadedAudit] = await Promise.all([
      getTravelRequest(session.value.token, requestId),
      getTravelRequestAudit(session.value.token, requestId),
    ]);
    request.value = loadedRequest;
    auditEvents.value = loadedAudit;
    state.value = "loaded";
  } catch (error) {
    if (error instanceof TravelMarketplaceApiError && error.status === 404) {
      state.value = "not-found";
      return;
    }
    state.value = "error";
    stateError.value = errorText(error);
  }
}

async function runAction(action: () => Promise<TravelRequest>) {
  if (!session.value || actionState.value === "submitting") return;
  actionState.value = "submitting";
  actionError.value = "";
  try {
    request.value = await action();
    auditEvents.value = await getTravelRequestAudit(session.value.token, requestId);
  } catch (error) {
    actionError.value = errorText(error);
  } finally {
    actionState.value = "idle";
  }
}

function startReview() {
  if (!session.value) return;
  const token = session.value.token;
  runAction(() => startTravelRequestReview(token, requestId));
}

function confirm() {
  if (!session.value) return;
  const token = session.value.token;
  runAction(() => confirmTravelRequest(token, requestId));
}

function complete() {
  if (!session.value) return;
  const token = session.value.token;
  runAction(() => completeTravelRequest(token, requestId));
}

const cancelDialogOpen = ref(false);
const cancelReason = ref<TravelRequestCancelReason>("CUSTOMER_REQUESTED");
const cancelDetail = ref("");

function openCancelDialog() {
  cancelReason.value = "CUSTOMER_REQUESTED";
  cancelDetail.value = "";
  cancelDialogOpen.value = true;
}

function confirmCancel() {
  if (!session.value) return;
  const token = session.value.token;
  const reason = cancelReason.value;
  const detail = cancelDetail.value.trim() || undefined;
  cancelDialogOpen.value = false;
  runAction(() => cancelTravelRequest(token, requestId, { reason, detail }));
}

// Exhaustive (not a CONFIRMED/COMPLETED-vs-everything-else binary, which
// previously showed "Awaiting confirmation" even for a cancelled or expired
// request) and localized to the customer's own recorded locale, not always
// English.
const customerStatusLabels: Record<TravelRequestStatus, { en: string; ar: string }> = {
  NEW: { en: "Received, awaiting review", ar: "تم الاستلام، في انتظار المراجعة" },
  IN_REVIEW: { en: "Under review", ar: "قيد المراجعة" },
  CONFIRMED: { en: "Confirmed", ar: "مؤكد" },
  COMPLETED: { en: "Completed", ar: "تم الإنجاز" },
  CANCELLED: { en: "Cancelled", ar: "ملغى" },
  EXPIRED: { en: "Expired", ar: "منتهي الصلاحية" },
};

const summaryText = computed(() => {
  if (!request.value) return "";
  const r = request.value;
  const isArabic = r.locale === "ar";
  const stateLabel = customerStatusLabels[r.status][isArabic ? "ar" : "en"];
  const serviceName = isArabic ? r.serviceName.ar : r.serviceName.en;
  const optionLabel = isArabic ? r.optionLabel.ar : r.optionLabel.en;
  return [
    `Sharm To Go — ${r.reference}`,
    `${serviceName} (${optionLabel})`,
    `Date: ${r.requestedDate}${r.requestedTime ? " " + r.requestedTime : ""}`,
    `Party: ${r.adults} adult(s)${r.children ? `, ${r.children} child(ren)` : ""}`,
    r.hotelOrPickup ? `Pickup: ${r.hotelOrPickup}` : null,
    `Status: ${stateLabel}`,
  ]
    .filter((line): line is string => line !== null)
    .join("\n");
});

const whatsappLink = computed(() => {
  if (!request.value?.customer.phone) return null;
  const digits = request.value.customer.phone.replace(/[^0-9]/g, "");
  return `https://wa.me/${digits}?text=${encodeURIComponent(summaryText.value)}`;
});

const copyState = ref<"idle" | "copied">("idle");
async function copySummary() {
  await navigator.clipboard.writeText(summaryText.value);
  copyState.value = "copied";
  setTimeout(() => (copyState.value = "idle"), 2000);
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

onMounted(() => {
  session.value = readAuthSession();
  load();
});
</script>

<template>
  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view this request.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <p v-if="!canView()" class="mt-8 text-sm text-wego-muted">
      Your account doesn't have permission to view requests (travel-request:view).
    </p>

    <template v-else>
      <p v-if="state === 'loading'" class="mt-8 text-sm text-wego-muted">Loading…</p>
      <WegoAlert v-else-if="state === 'error'" variant="danger" class="mt-8">{{ stateError }}</WegoAlert>

      <div v-else-if="state === 'not-found'" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
        <p>No request with this id.</p>
        <NuxtLink to="/requests" class="mt-3 inline-block text-wego-accent underline">Back to requests</NuxtLink>
      </div>

      <template v-else-if="request">
        <WegoPageHeader eyebrow="Sharm To Go" :title="request.reference" :description="request.serviceName.en" />
        <WegoBadge :tone="statusTone(request.status)" class="mt-3">{{ request.status }}</WegoBadge>

        <WegoAlert v-if="actionError" variant="danger" class="mt-6">{{ actionError }}</WegoAlert>

        <div class="mt-8 grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <div class="space-y-6">
            <WegoPanel title="Request">
              <dl class="grid gap-4 sm:grid-cols-2">
                <div>
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Service</dt>
                  <dd class="mt-1">{{ request.serviceName.en }} — {{ request.optionLabel.en }}</dd>
                </div>
                <div>
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Price (snapshot)</dt>
                  <dd class="reference mt-1">{{ request.priceCurrency }} {{ request.priceAmount }} · {{ request.priceBasis }}</dd>
                </div>
                <div>
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Date</dt>
                  <dd class="mt-1">{{ request.requestedDate }}<span v-if="request.requestedTime"> · {{ request.requestedTime }}</span></dd>
                </div>
                <div>
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Party</dt>
                  <dd class="mt-1">{{ request.adults }} adult(s)<span v-if="request.children"> · {{ request.children }} child(ren)</span></dd>
                </div>
                <div v-if="request.hotelOrPickup">
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Hotel / pickup</dt>
                  <dd class="mt-1">{{ request.hotelOrPickup }}</dd>
                </div>
                <div>
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Source</dt>
                  <dd class="mt-1">{{ request.sourceChannel }} · {{ request.locale.toUpperCase() }}</dd>
                </div>
                <div v-if="request.notes" class="sm:col-span-2">
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Notes</dt>
                  <dd class="mt-1">{{ request.notes }}</dd>
                </div>
                <div class="sm:col-span-2">
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Cancellation policy</dt>
                  <dd class="mt-1 text-sm text-wego-muted">{{ request.cancellationPolicy.en }}</dd>
                </div>
              </dl>
            </WegoPanel>

            <WegoPanel title="Customer">
              <dl class="grid gap-4 sm:grid-cols-2">
                <div>
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Name</dt>
                  <dd class="mt-1">{{ request.customer.name }}</dd>
                </div>
                <div v-if="request.customer.phone">
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Phone</dt>
                  <dd class="reference mt-1">{{ request.customer.phone }}</dd>
                </div>
                <div v-if="request.customer.email">
                  <dt class="text-xs font-semibold tracking-wide text-wego-muted uppercase">Email</dt>
                  <dd class="mt-1">{{ request.customer.email }}</dd>
                </div>
              </dl>
            </WegoPanel>

            <WegoPanel title="Customer-safe summary">
              <p class="text-sm text-wego-muted">
                No payment has been collected for this request — confirmation and payment are tracked independently, and
                online payment is not live yet for this client.
              </p>
              <pre class="mt-4 rounded-wego-control border border-wego-border bg-wego-surface-sunken p-4 text-sm whitespace-pre-wrap">{{ summaryText }}</pre>
              <div class="mt-4 flex flex-wrap gap-3">
                <WegoButton type="button" variant="secondary" @click="copySummary">
                  {{ copyState === "copied" ? "Copied!" : "Copy summary" }}
                </WegoButton>
                <a
                  v-if="whatsappLink"
                  :href="whatsappLink"
                  target="_blank"
                  rel="noopener"
                  class="inline-flex min-h-11 items-center rounded-wego-control border border-wego-border px-4 text-sm font-semibold text-wego-ink hover:bg-wego-surface-hover"
                >
                  Message on WhatsApp
                </a>
              </div>
            </WegoPanel>

            <WegoPanel title="Audit timeline">
              <p v-if="auditEvents.length === 0" class="text-sm text-wego-muted">No events recorded yet.</p>
              <ol v-else class="space-y-3">
                <li v-for="event in auditEvents" :key="event.id" class="rounded-wego-control border border-wego-border p-3">
                  <p class="font-semibold">
                    <span v-if="event.fromStatus">{{ event.fromStatus }} → </span>{{ event.toStatus }}
                  </p>
                  <p class="mt-1 text-sm text-wego-muted">
                    {{ formatDateTime(event.occurredAt) }} · {{ event.actorType }}
                    <span v-if="event.reason"> · {{ event.reason }}</span>
                  </p>
                  <p v-if="event.detail" class="mt-1 text-sm text-wego-muted">{{ event.detail }}</p>
                </li>
              </ol>
            </WegoPanel>
          </div>

          <div class="space-y-4">
            <WegoPanel title="Actions">
              <div class="flex flex-col gap-3">
                <WegoButton
                  v-if="canReview() && request.status === 'NEW'"
                  type="button"
                  :disabled="actionState === 'submitting'"
                  :loading="actionState === 'submitting'"
                  @click="startReview"
                >
                  Claim for review
                </WegoButton>
                <WegoButton
                  v-if="canConfirm() && (request.status === 'NEW' || request.status === 'IN_REVIEW')"
                  type="button"
                  :disabled="actionState === 'submitting'"
                  :loading="actionState === 'submitting'"
                  @click="confirm"
                >
                  Confirm
                </WegoButton>
                <WegoButton
                  v-if="canComplete() && request.status === 'CONFIRMED'"
                  type="button"
                  :disabled="actionState === 'submitting'"
                  :loading="actionState === 'submitting'"
                  @click="complete"
                >
                  Mark completed
                </WegoButton>
                <WegoButton
                  v-if="canCancel() && !['COMPLETED', 'CANCELLED', 'EXPIRED'].includes(request.status)"
                  type="button"
                  variant="secondary"
                  :disabled="actionState === 'submitting'"
                  @click="openCancelDialog"
                >
                  Cancel request
                </WegoButton>
                <p
                  v-if="!canReview() && !canConfirm() && !canComplete() && !canCancel()"
                  class="text-sm text-wego-muted"
                >
                  Your account has no action permissions for requests.
                </p>
              </div>
            </WegoPanel>
            <NuxtLink to="/requests" class="inline-block text-sm font-semibold text-wego-accent underline">Back to requests</NuxtLink>
          </div>
        </div>

        <WegoDialog :open="cancelDialogOpen" title="Cancel this request?" @close="cancelDialogOpen = false">
          <div class="space-y-4">
            <WegoSelect id="cancelReason" v-model="cancelReason" label="Reason">
              <option value="CUSTOMER_REQUESTED">Customer requested</option>
              <option value="STAFF_REJECTED">Staff rejected</option>
              <option value="SERVICE_UNAVAILABLE">Service unavailable</option>
              <option value="DUPLICATE_REQUEST">Duplicate request</option>
              <option value="OTHER">Other</option>
            </WegoSelect>
            <WegoTextarea id="cancelDetail" v-model="cancelDetail" label="Detail (optional)" />
          </div>
          <template #actions>
            <WegoButton type="button" variant="secondary" @click="cancelDialogOpen = false">Keep request</WegoButton>
            <WegoButton type="button" variant="destructive" @click="confirmCancel">Confirm cancellation</WegoButton>
          </template>
        </WegoDialog>
      </template>
    </template>
  </template>
</template>
