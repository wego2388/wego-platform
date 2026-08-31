<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoFoundationCard } from "@wego/ui";
import { type AuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  type BookingsDashboard,
  type DiversDashboard,
  type EquipmentDashboard,
  getBookingsDashboard,
  getDiversDashboard,
  getEquipmentDashboard,
  getOfferingsDashboard,
  type OfferingsDashboard,
} from "../composables/useDashboardApi";

useHead({
  title: "Wego Platform",
  meta: [
    {
      name: "description",
      content: "Product-neutral Wego operations workspace foundation",
    },
  ],
});

const session = ref<AuthSession | null>(null);
const bookingsDashboard = ref<BookingsDashboard | null>(null);
const offeringsDashboard = ref<OfferingsDashboard | null>(null);
const diversDashboard = ref<DiversDashboard | null>(null);
const equipmentDashboard = ref<EquipmentDashboard | null>(null);
const dashboardError = ref("");

async function loadDashboard() {
  if (!session.value) return;
  const token = session.value.token;
  dashboardError.value = "";
  try {
    const tasks: Promise<void>[] = [];
    if (hasPermission(session.value, "booking:view")) {
      tasks.push(getBookingsDashboard(token).then((data) => void (bookingsDashboard.value = data)));
    }
    if (hasPermission(session.value, "offering:view")) {
      tasks.push(getOfferingsDashboard(token).then((data) => void (offeringsDashboard.value = data)));
    }
    if (hasPermission(session.value, "diver:view")) {
      tasks.push(getDiversDashboard(token).then((data) => void (diversDashboard.value = data)));
    }
    if (hasPermission(session.value, "equipment:view")) {
      tasks.push(getEquipmentDashboard(token).then((data) => void (equipmentDashboard.value = data)));
    }
    await Promise.all(tasks);
  } catch {
    dashboardError.value = "Could not load the live business summary. Check your connection and try again.";
  }
}

onMounted(() => {
  session.value = readAuthSession();
  loadDashboard();
});

const foundationItems = [
  {
    title: "Secure by default",
    state: "ready" as const,
    description: "Health is public; every business route remains denied until explicitly authorized.",
  },
  {
    title: "Versioned contracts",
    state: "ready" as const,
    description: "Web clients integrate through the validated Wego OpenAPI contract.",
  },
  {
    title: "Identity foundation",
    state: "ready" as const,
    description: "Real login issues a session; permissions are resolved from assigned roles.",
  },
  {
    title: "Business modules",
    state: "ready" as const,
    description: "Wego Divers bookings are live — see Offerings and Bookings above.",
  },
];
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">
      <header class="flex max-w-3xl flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">
            Wego Platform
          </p>
          <h1 class="mt-4 text-4xl font-semibold tracking-tight sm:text-6xl">
            A calm foundation for serious operations.
          </h1>
          <p class="mt-6 text-lg leading-8 text-wego-muted">
            This product-neutral shell proves the shared web boundary. It contains no client-specific
            workflows and makes no authorization decisions.
          </p>
        </div>
        <div class="flex shrink-0 flex-wrap items-center gap-4">
          <NuxtLink to="/offerings" class="text-sm font-semibold text-wego-accent underline">Offerings</NuxtLink>
          <NuxtLink to="/bookings" class="text-sm font-semibold text-wego-accent underline">Bookings</NuxtLink>
          <NuxtLink to="/divers" class="text-sm font-semibold text-wego-accent underline">Divers</NuxtLink>
          <NuxtLink to="/equipment" class="text-sm font-semibold text-wego-accent underline">Equipment</NuxtLink>
          <NuxtLink to="/boat-charters" class="text-sm font-semibold text-wego-accent underline">Boat Charters</NuxtLink>
          <NuxtLink to="/course-enrollments" class="text-sm font-semibold text-wego-accent underline">Courses</NuxtLink>
          <NuxtLink to="/accounts" class="text-sm font-semibold text-wego-accent underline">Accounts</NuxtLink>
          <NuxtLink to="/roles" class="text-sm font-semibold text-wego-accent underline">Roles</NuxtLink>
          <NuxtLink to="/employees" class="text-sm font-semibold text-wego-accent underline">Employees</NuxtLink>
          <NuxtLink to="/attendance" class="text-sm font-semibold text-wego-accent underline">Attendance</NuxtLink>
          <NuxtLink to="/leave-requests" class="text-sm font-semibold text-wego-accent underline">Leave Requests</NuxtLink>
          <NuxtLink to="/chart-of-accounts" class="text-sm font-semibold text-wego-accent underline">Chart of Accounts</NuxtLink>
          <NuxtLink to="/journal-entries" class="text-sm font-semibold text-wego-accent underline">Journal Entries</NuxtLink>
          <NuxtLink
            to="/login"
            class="rounded-wego-control bg-wego-accent px-5 py-2.5 text-sm font-semibold whitespace-nowrap text-white transition-opacity hover:opacity-90"
          >
            Sign in
          </NuxtLink>
        </div>
      </header>

      <section v-if="session" aria-labelledby="live-summary" class="mt-14">
        <h2 id="live-summary" class="text-xl font-semibold">Live business summary</h2>
        <WegoAlert v-if="dashboardError" variant="danger" class="mt-4">{{ dashboardError }}</WegoAlert>
        <div class="mt-6 grid gap-5 md:grid-cols-2 xl:grid-cols-4">
          <div v-if="bookingsDashboard" class="rounded-wego-card border border-wego-border bg-wego-surface p-5">
            <p class="text-sm font-medium text-wego-muted">Bookings today</p>
            <p class="mt-2 text-3xl font-semibold">{{ bookingsDashboard.bookingsToday }}</p>
            <p class="mt-3 text-sm font-medium text-wego-muted">Paid this month</p>
            <p v-if="bookingsDashboard.paidRevenueThisMonth.length === 0" class="mt-1 text-sm text-wego-muted">Nothing yet.</p>
            <p v-for="revenue in bookingsDashboard.paidRevenueThisMonth" :key="revenue.currencyCode" class="mt-1 text-lg font-semibold">
              {{ revenue.amount }} {{ revenue.currencyCode }}
            </p>
          </div>

          <div v-if="offeringsDashboard" class="rounded-wego-card border border-wego-border bg-wego-surface p-5">
            <p class="text-sm font-medium text-wego-muted">Coming up (next 7 days)</p>
            <p v-if="offeringsDashboard.upcomingTrips.length === 0" class="mt-2 text-sm text-wego-muted">Nothing scheduled.</p>
            <ul v-else class="mt-2 space-y-2">
              <li v-for="trip in offeringsDashboard.upcomingTrips.slice(0, 5)" :key="trip.id" class="text-sm">
                <span class="font-semibold">{{ trip.title }}</span> · {{ trip.startsOn }}
              </li>
            </ul>
          </div>

          <div v-if="diversDashboard" class="rounded-wego-card border border-wego-border bg-wego-surface p-5">
            <p class="text-sm font-medium text-wego-muted">Active divers</p>
            <p class="mt-2 text-3xl font-semibold">{{ diversDashboard.activeDivers }}</p>
          </div>

          <div v-if="equipmentDashboard" class="rounded-wego-card border border-wego-border bg-wego-surface p-5">
            <p class="text-sm font-medium text-wego-muted">Equipment status</p>
            <p class="mt-2 text-sm">Active: <span class="font-semibold">{{ equipmentDashboard.active }}</span></p>
            <p class="mt-1 text-sm">In maintenance: <span class="font-semibold">{{ equipmentDashboard.inMaintenance }}</span></p>
            <p class="mt-1 text-sm">Retired: <span class="font-semibold">{{ equipmentDashboard.retired }}</span></p>
          </div>
        </div>
        <p
          v-if="!bookingsDashboard && !offeringsDashboard && !diversDashboard && !equipmentDashboard && !dashboardError"
          class="mt-4 text-sm text-wego-muted"
        >
          Your account doesn't hold permission to view any business summary widget yet.
        </p>
      </section>

      <section aria-labelledby="foundation-status" class="mt-14">
        <h2 id="foundation-status" class="text-xl font-semibold">Foundation status</h2>
        <div class="mt-6 grid gap-5 md:grid-cols-3">
          <WegoFoundationCard
            v-for="item in foundationItems"
            :key="item.title"
            :title="item.title"
            :state="item.state"
            :description="item.description"
          />
        </div>
      </section>
    </div>
  </main>
</template>
