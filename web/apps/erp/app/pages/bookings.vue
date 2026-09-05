<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput, WegoPageHeader, WegoPagination, WegoPanel, WegoSelect } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  type Booking,
  cancelBooking,
  createBooking,
  DiversApiError,
  getOffering,
  listBookings,
  listOfferings,
  markBookingPaid,
  type Offering,
  PAGE_SIZE,
  refundBooking,
} from "../composables/useDiversApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Bookings · Wego Platform" });

const session = ref<AuthSession | null>(null);
const bookings = ref<Booking[]>([]);
const activeOfferings = ref<Offering[]>([]);
// A lookup for display only (offering title/date next to each booking row).
// Seeded from the first page of every offering regardless of status, so a
// booking against a now-closed offering still shows a name — not just the
// active ones the create-form dropdown needs. Never silently falls back to
// a raw id for the rest of the catalog: loadAll() backfills any booking's
// offering that first page didn't cover with one individual GET each.
const offeringsById = ref<Record<string, Offering>>({});
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const page = ref(0);
const hasNextPage = ref(false);

const form = ref({
  offeringId: "",
  partySize: "1",
  customerName: "",
  customerEmail: "",
  customerPhone: "",
});
const createState = ref<"idle" | "submitting" | "error">("idle");
const createError = ref("");
// Generated once per booking attempt, not per submit() call: a retry after
// a network error (the request may have actually reached the server) must
// reuse the same key, or idempotency protects nothing — a fresh key on
// every click would let a retry create a genuine duplicate booking. Only
// rotated after a successful creation, when the next click starts a new
// logical attempt.
const idempotencyKey = ref(crypto.randomUUID());

// Per-booking in-flight action state, keyed by booking id, so one row's
// cancel/payment action in flight doesn't disable every other row.
const actionState = ref<Record<string, "idle" | "submitting" | "error">>({});
const actionError = ref<Record<string, string>>({});
const cancelReason = ref<Record<string, string>>({});
const refundReason = ref<Record<string, string>>({});

const canCreate = computed(() => hasPermission(session.value, "booking:create"));
const canViewBookings = computed(() => hasPermission(session.value, "booking:view"));
const canViewOfferings = computed(() => hasPermission(session.value, "offering:view"));
const canCancel = computed(() => hasPermission(session.value, "booking:cancel"));
// Deliberately distinct permission checks, not one "can manage payment"
// flag — booking:create alone must not enable either button, and mark-paid
// and refund are independently grantable (SECURITY_MODEL.md).
const canMarkPaid = computed(() => hasPermission(session.value, "booking:payment-update"));
const canRefund = computed(() => hasPermission(session.value, "booking:refund"));

function handleApiError(error: unknown) {
  if (error instanceof DiversApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof DiversApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "offering_closed") return "That offering is closed.";
    if (error.errorCode === "offering_not_found") return "That offering no longer exists.";
    if (error.errorCode === "capacity_exceeded") return "That offering is fully booked.";
    if (error.errorCode === "already_cancelled") return "This booking is already cancelled.";
    if (error.errorCode === "invalid_payment_transition") return "That payment status change isn't allowed from here.";
    if (error.errorCode === "idempotency_key_conflict") return "This request conflicts with a previous, different request.";
    if (error.status === 404) return "Not found.";
    if (error.status === 400) return "That request was invalid — please check the fields and try again.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

function offeringLabel(offeringId: string): string {
  const offering = offeringsById.value[offeringId];
  return offering ? `${offering.title} — ${offering.startsOn}` : offeringId;
}

async function loadAll() {
  if (!session.value) return;
  listState.value = "loading";
  listError.value = "";
  const token = session.value.token;
  try {
    // Each fetch is gated on the specific permission its endpoint requires
    // — a session missing booking:view or offering:view never calls that
    // endpoint at all, rather than firing a request guaranteed to 403.
    const [bookingsResult, activeResult, allOfferingsFirstPage] = await Promise.all([
      canViewBookings.value ? listBookings(token, { page: page.value }) : Promise.resolve([]),
      // The create-booking dropdown needs every active offering, not just
      // a first page — 200 is the API's own hard cap on the `size` query
      // parameter (see OfferingController.list), so this is the largest
      // single fetch possible without a dedicated search/autocomplete UI,
      // which isn't warranted at this scale.
      canViewOfferings.value ? listOfferings(token, { status: "ACTIVE", size: 200 }) : Promise.resolve([]),
      canViewOfferings.value ? listOfferings(token) : Promise.resolve([]),
    ]);
    bookings.value = bookingsResult;
    hasNextPage.value = bookingsResult.length === PAGE_SIZE;
    activeOfferings.value = activeResult;
    offeringsById.value = Object.fromEntries(allOfferingsFirstPage.map((offering) => [offering.id, offering]));
    listState.value = "loaded";

    // Backfill any booking whose offering fell outside that first page —
    // keeps the display correct regardless of catalog size instead of
    // silently showing a raw id once the catalog passes one page. Only
    // possible with offering:view; without it, offeringLabel() falls back
    // to the raw id, which is the best this session's permissions allow.
    if (canViewOfferings.value) {
      const missingOfferingIds = [...new Set(bookingsResult.map((booking) => booking.offeringId))].filter(
        (offeringId) => !(offeringId in offeringsById.value),
      );
      if (missingOfferingIds.length > 0) {
        const fetched = await Promise.all(missingOfferingIds.map((offeringId) => getOffering(token, offeringId)));
        offeringsById.value = { ...offeringsById.value, ...Object.fromEntries(fetched.map((offering) => [offering.id, offering])) };
      }
    }
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function nextPage() {
  page.value += 1;
  loadAll();
}

function previousPage() {
  if (page.value === 0) return;
  page.value -= 1;
  loadAll();
}

async function submitCreate() {
  if (!session.value) return;
  createState.value = "submitting";
  createError.value = "";
  try {
    const created = await createBooking(session.value.token, idempotencyKey.value, {
      offeringId: form.value.offeringId,
      partySize: Number(form.value.partySize),
      customerName: form.value.customerName,
      customerEmail: form.value.customerEmail || undefined,
      customerPhone: form.value.customerPhone || undefined,
    });
    bookings.value = [created, ...bookings.value];
    // The dropdown's own options already carry the full Offering the
    // customer just booked — reuse that instead of an extra GET, so the
    // display never shows a raw id for a booking created this session.
    const selectedOffering = activeOfferings.value.find((offering) => offering.id === created.offeringId);
    if (selectedOffering && !(selectedOffering.id in offeringsById.value)) {
      offeringsById.value = { ...offeringsById.value, [selectedOffering.id]: selectedOffering };
    }
    form.value = { offeringId: "", partySize: "1", customerName: "", customerEmail: "", customerPhone: "" };
    // This attempt succeeded — rotate the key so the next "Create" click
    // starts a genuinely new idempotent attempt rather than replaying this
    // one (a stale key here would make the *next* real booking bounce off
    // this one as a false "replay").
    idempotencyKey.value = crypto.randomUUID();
    createState.value = "idle";
  } catch (error) {
    handleApiError(error);
    createState.value = "error";
    createError.value = errorText(error);
  }
}

async function submitCancel(booking: Booking) {
  if (!session.value) return;
  const reason = (cancelReason.value[booking.id] ?? "").trim();
  if (!reason) {
    actionState.value[booking.id] = "error";
    actionError.value[booking.id] = "A cancellation reason is required.";
    return;
  }
  if (!window.confirm(`Cancel this booking for ${booking.customerName}? This cannot be undone.`)) return;

  actionState.value[booking.id] = "submitting";
  actionError.value[booking.id] = "";
  try {
    const updated = await cancelBooking(session.value.token, booking.id, reason);
    bookings.value = bookings.value.map((existing) => (existing.id === updated.id ? updated : existing));
    actionState.value[booking.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[booking.id] = "error";
    actionError.value[booking.id] = errorText(error);
  }
}

async function submitMarkPaid(booking: Booking) {
  if (!session.value) return;
  actionState.value[booking.id] = "submitting";
  actionError.value[booking.id] = "";
  try {
    const updated = await markBookingPaid(session.value.token, booking.id);
    bookings.value = bookings.value.map((existing) => (existing.id === updated.id ? updated : existing));
    actionState.value[booking.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[booking.id] = "error";
    actionError.value[booking.id] = errorText(error);
  }
}

async function submitRefund(booking: Booking) {
  if (!session.value) return;
  const reason = (refundReason.value[booking.id] ?? "").trim();
  if (!reason) {
    actionState.value[booking.id] = "error";
    actionError.value[booking.id] = "A refund reason is required.";
    return;
  }
  if (!window.confirm(`Refund this booking for ${booking.customerName}? This cannot be undone.`)) return;

  actionState.value[booking.id] = "submitting";
  actionError.value[booking.id] = "";
  try {
    const updated = await refundBooking(session.value.token, booking.id, reason);
    bookings.value = bookings.value.map((existing) => (existing.id === updated.id ? updated : existing));
    actionState.value[booking.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[booking.id] = "error";
    actionError.value[booking.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadAll();
});
</script>

<template>
  <WegoPageHeader title="Bookings" description="Reservations against the live diving offerings catalog." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view bookings.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel v-if="canCreate" title="New booking" class="mt-8">
      <WegoAlert v-if="!canViewOfferings" variant="warning" class="mb-4">
        Your account can create bookings but not list offerings, so no offering can be selected below. Ask an
        administrator to also grant offering:view.
      </WegoAlert>
      <form class="space-y-5" @submit.prevent="submitCreate">
        <WegoSelect id="offeringId" v-model="form.offeringId" label="Offering" required>
          <option value="" disabled>Select an active offering</option>
          <option v-for="offering in activeOfferings" :key="offering.id" :value="offering.id">
            {{ offering.title }} — {{ offering.startsOn }} ({{ offering.unitPrice.amount }}
            {{ offering.unitPrice.currencyCode }})
          </option>
        </WegoSelect>
        <WegoInput id="partySize" v-model="form.partySize" label="Party size" type="number" required />
        <WegoInput id="customerName" v-model="form.customerName" label="Customer name" required />
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="customerEmail" v-model="form.customerEmail" label="Customer email (or phone)" type="email" />
          <WegoInput id="customerPhone" v-model="form.customerPhone" label="Customer phone (or email)" />
        </div>

        <WegoAlert v-if="createState === 'error'" variant="danger">{{ createError }}</WegoAlert>

        <WegoButton
          type="submit"
          :disabled="createState === 'submitting' || !form.offeringId"
          :loading="createState === 'submitting'"
        >
          {{ createState === "submitting" ? "Creating…" : "Create booking" }}
        </WegoButton>
      </form>
    </WegoPanel>

    <WegoPanel title="Bookings" class="mt-8">
      <p v-if="!canViewBookings" class="text-sm text-wego-muted">
        Your account doesn't have permission to list bookings (booking:view).
      </p>
      <p v-else-if="listState === 'loading'" class="text-sm text-wego-muted">Loading…</p>
      <p v-else-if="listState === 'loaded' && bookings.length === 0 && page === 0" class="text-sm text-wego-muted">
        No bookings yet.
      </p>
      <!-- Row stays a plain <li>, and the status/payment line below stays
           exact literal text ("payment PAID", "CANCELLED (reason)") on
           purpose — the real E2E suite locates each row with
           locator("li", { hasText: CUSTOMER_NAME }) and asserts these
           exact substrings; restructuring either would silently break a
           passing test, not just a visual detail. -->
      <ul v-else-if="bookings.length > 0" class="space-y-3">
        <li v-for="booking in bookings" :key="booking.id" class="rounded-wego-control border border-wego-border p-4">
          <p class="font-semibold">{{ booking.customerName }} · {{ booking.partySize }} pax</p>
          <p class="mt-1 text-sm text-wego-muted">{{ offeringLabel(booking.offeringId) }}</p>
          <p class="mt-1 text-sm text-wego-muted">
            <span v-if="booking.customerEmail">{{ booking.customerEmail }}</span>
            <span v-if="booking.customerEmail && booking.customerPhone"> · </span>
            <span v-if="booking.customerPhone">{{ booking.customerPhone }}</span>
          </p>
          <p class="mt-1 text-sm text-wego-muted">
            {{ booking.status }}<span v-if="booking.cancellationReason"> ({{ booking.cancellationReason }})</span>
            · payment {{ booking.paymentStatus }} · unit {{ booking.unitPrice.amount }}
            {{ booking.unitPrice.currencyCode }} × {{ booking.billableQuantity }} = total
            {{ booking.totalPrice.amount }} {{ booking.totalPrice.currencyCode }}
          </p>

          <WegoAlert v-if="actionState[booking.id] === 'error'" variant="danger" class="mt-2">
            {{ actionError[booking.id] }}
          </WegoAlert>

          <div v-if="canCancel && booking.status === 'CONFIRMED'" class="mt-3 flex flex-wrap items-end gap-2">
            <WegoInput
              :id="`cancel-reason-${booking.id}`"
              :model-value="cancelReason[booking.id] ?? ''"
              label="Cancellation reason"
              class="min-w-0 flex-1"
              @update:model-value="(value) => (cancelReason[booking.id] = value)"
            />
            <WegoButton
              type="button"
              variant="secondary"
              :disabled="actionState[booking.id] === 'submitting'"
              @click="submitCancel(booking)"
            >
              Cancel
            </WegoButton>
          </div>

          <div v-if="canMarkPaid && booking.paymentStatus === 'UNPAID' && booking.status !== 'CANCELLED'" class="mt-3">
            <WegoButton
              type="button"
              variant="secondary"
              :disabled="actionState[booking.id] === 'submitting'"
              @click="submitMarkPaid(booking)"
            >
              Mark paid
            </WegoButton>
          </div>

          <div v-if="canRefund && booking.paymentStatus === 'PAID'" class="mt-3 flex flex-wrap items-end gap-2">
            <WegoInput
              :id="`refund-reason-${booking.id}`"
              :model-value="refundReason[booking.id] ?? ''"
              label="Refund reason"
              class="min-w-0 flex-1"
              @update:model-value="(value) => (refundReason[booking.id] = value)"
            />
            <WegoButton
              type="button"
              variant="secondary"
              :disabled="actionState[booking.id] === 'submitting'"
              @click="submitRefund(booking)"
            >
              Refund
            </WegoButton>
          </div>
        </li>
      </ul>

      <WegoPagination v-if="listState === 'loaded'" class="mt-4" :page="page" :has-next-page="hasNextPage" @previous="previousPage" @next="nextPage" />
    </WegoPanel>
  </template>
</template>
