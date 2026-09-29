<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  logoutAuthSession,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import {
  listBookings,
  listAllStaffTours,
  addMoney,
  formatMoney,
  type Booking,
  type Tour,
  ToursApiError,
} from "../composables/useToursApi";

useHead({ title: "Overview · Safari Tours Sharm" });

const router = useRouter();
const session = ref<AuthSession | null>(null);
const bookings = ref<Booking[]>([]);
const toursById = ref<Record<string, Tour>>({});
const state = ref<"idle" | "loading" | "loaded" | "error">("idle");
const errorMsg = ref("");

const canViewBookings = computed(() => hasPermission(session.value, "tours-operator.booking:view"));
const canViewTours    = computed(() => hasPermission(session.value, "tours-operator.tour:view"));
const canViewStaff    = computed(() =>
  hasPermission(session.value, "identity:user-view") || hasPermission(session.value, "identity:role-view"));

// Today's date (Africa/Cairo = UTC+3, use offset string for display)
const todayIso = new Date().toLocaleDateString("sv-SE", { timeZone: "Africa/Cairo" });

const todayBookings = computed(() =>
  bookings.value.filter((b) => b.tourDate === todayIso && b.status !== "EXPIRED"),
);
const todayRevenue = computed(() =>
  addMoney(todayBookings.value
    .filter((b) => b.status === "CONFIRMED" || b.status === "COMPLETED")
    .map((b) => b.totalPrice)),
);
const pendingConfirm = computed(() =>
  bookings.value.filter((b) => b.status === "NEW").length,
);
const upcomingCount = computed(() =>
  bookings.value.filter((b) => b.tourDate > todayIso && b.status === "CONFIRMED").length,
);

const recentBookings = computed(() =>
  [...bookings.value]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 10),
);

function tourName(tourId: string): string {
  return toursById.value[tourId]?.slug ?? tourId;
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

function errorText(err: unknown): string {
  if (err instanceof ToursApiError) {
    if (err.status === 401) return "Session expired. Redirecting to sign in…";
    if (err.status === 403) return "You don't have permission to view this.";
    return `Request failed (${err.errorCode}).`;
  }
  return "Could not reach the server.";
}

async function logout() {
  await logoutAuthSession(session.value);
  void router.replace("/login");
}

async function load() {
  if (!session.value) return;
  state.value = "loading";
  errorMsg.value = "";
  const token = session.value.token;
  try {
    const [bookingsResult, toursResult] = await Promise.all([
      canViewBookings.value ? listBookings(token, { size: 100 }) : Promise.resolve([] as Booking[]),
      canViewTours.value    ? listAllStaffTours(token) : Promise.resolve([] as Tour[]),
    ]);
    bookings.value = bookingsResult;
    toursById.value = Object.fromEntries(toursResult.map((t) => [t.id, t]));
    state.value = "loaded";
  } catch (err) {
    handleApiError(err);
    state.value = "error";
    errorMsg.value = errorText(err);
  }
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
  <main class="min-h-screen bg-sts-canvas px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">

      <header class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p class="text-sm font-semibold tracking-widest text-sts-muted uppercase">
            Safari Tours Sharm
          </p>
          <h1 class="mt-2 text-3xl font-semibold tracking-tight">Overview</h1>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-sm text-sts-muted">{{ session?.email }}</span>
          <button
            type="button"
            class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
            @click="logout"
          >
            Sign out
          </button>
        </div>
      </header>

      <!-- Nav -->
      <nav class="mt-6 flex flex-wrap gap-2" aria-label="Dashboard navigation">
        <NuxtLink
          to="/"
          class="rounded-xl bg-sts-ocean px-4 py-2 text-sm font-semibold text-white"
        >
          Overview
        </NuxtLink>
        <NuxtLink
          to="/bookings"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
        >
          Bookings
        </NuxtLink>
        <NuxtLink
          to="/tours"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
        >
          Tours
        </NuxtLink>
        <NuxtLink
          to="/finance"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
        >
          Finance
        </NuxtLink>
        <NuxtLink
          to="/customers"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
        >
          Customers
        </NuxtLink>
        <NuxtLink
          to="/reviews"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
        >
          Reviews
        </NuxtLink>
        <NuxtLink
          to="/notifications"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
        >
          Notifications
        </NuxtLink>
        <NuxtLink
          to="/settings"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
        >
          Settings
        </NuxtLink>
        <NuxtLink
          v-if="canViewStaff"
          to="/staff"
          class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold text-sts-ink hover:bg-sts-canvas"
        >
          Staff
        </NuxtLink>
      </nav>

      <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ errorMsg }}</WegoAlert>

      <!-- KPI cards -->
      <section v-if="state !== 'idle'" aria-labelledby="kpi-heading" class="mt-8">
        <h2 id="kpi-heading" class="sr-only">Key metrics</h2>
        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <article
            v-for="kpi in [
              { label: 'Today\'s Bookings', value: state === 'loaded' ? String(todayBookings.length) : '…', sub: 'tours today' },
              { label: 'Today\'s Tour Value', value: state === 'loaded' ? formatMoney(todayRevenue) : '…', sub: 'booked value of today\'s tours — revenue is on Finance' },
              { label: 'Pending Confirm',   value: state === 'loaded' ? String(pendingConfirm) : '…',      sub: 'awaiting payment confirm' },
              { label: 'Upcoming',          value: state === 'loaded' ? String(upcomingCount) : '…',       sub: 'confirmed future bookings' },
            ]"
            :key="kpi.label"
            class="rounded-2xl border border-sts-border bg-sts-surface p-5 shadow-sm"
          >
            <p class="text-sm font-medium text-sts-muted">{{ kpi.label }}</p>
            <p class="mt-3 text-3xl font-bold tracking-tight text-sts-ocean">{{ kpi.value }}</p>
            <p class="mt-1 text-xs text-sts-muted">{{ kpi.sub }}</p>
          </article>
        </div>
      </section>

      <!-- Recent bookings -->
      <section class="mt-10">
        <div class="flex items-center justify-between">
          <h2 class="text-xl font-semibold">Recent Bookings</h2>
          <NuxtLink to="/bookings" class="text-sm font-semibold text-sts-ocean hover:underline">
            View all →
          </NuxtLink>
        </div>

        <p v-if="state === 'loading'" class="mt-4 text-sm text-sts-muted">Loading…</p>
        <p v-else-if="!canViewBookings" class="mt-4 text-sm text-sts-muted">
          Your account doesn't have permission to view bookings (tours-operator.booking:view).
        </p>
        <p v-else-if="state === 'loaded' && recentBookings.length === 0" class="mt-4 text-sm text-sts-muted">
          No bookings yet.
        </p>

        <div v-else-if="recentBookings.length > 0" class="mt-4 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface shadow-sm">
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-sts-border bg-sts-canvas/60">
                  <th class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Ref</th>
                  <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Customer</th>
                  <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Tour</th>
                  <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Date</th>
                  <th class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Pax</th>
                  <th class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">Total</th>
                  <th class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Status</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="b in recentBookings"
                  :key="b.id"
                  class="cursor-pointer border-b border-sts-border/50 last:border-0 hover:bg-sts-canvas/50"
                  @click="$router.push(`/bookings/${b.id}`)"
                >
                  <td class="ref px-5 py-3.5 font-mono text-xs text-sts-muted">{{ b.reference }}</td>
                  <td class="px-4 py-3.5 font-medium">{{ b.customer.fullName }}</td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ tourName(b.tourId) }}</td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ b.tourDate }}</td>
                  <td class="px-4 py-3.5">{{ b.adultsCount + b.childrenCount }}</td>
                  <td class="money px-4 py-3.5 text-end font-semibold">{{ formatMoney(b.totalPrice) }}</td>
                  <td class="px-5 py-3.5">
                    <span :class="`badge badge-${b.status}`">{{ b.status }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

    </div>
  </main>
</template>
