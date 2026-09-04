<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoPageHeader, WegoPanel } from "@wego/ui";
import { type AuthSession, readAuthSession, hasPermission } from "../composables/useAuthSession";
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

definePageMeta({ layout: "app-shell" });

useHead({
  title: "Dashboard · Wego Platform",
  meta: [{ name: "description", content: "Wego Platform staff operations dashboard." }],
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
</script>

<template>
  <WegoPageHeader title="Dashboard" description="Today's real business activity across the platform." />

  <p v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6 text-wego-muted">
    You need to sign in to view the dashboard.
  </p>

  <section v-else aria-labelledby="live-summary" class="mt-10">
    <h2 id="live-summary" class="text-xl font-semibold">Live business summary</h2>
    <WegoAlert v-if="dashboardError" variant="danger" class="mt-4">{{ dashboardError }}</WegoAlert>
    <div class="mt-6 grid gap-5 md:grid-cols-2 xl:grid-cols-4">
      <WegoPanel v-if="bookingsDashboard">
        <p class="text-sm font-medium text-wego-muted">Bookings today</p>
        <p class="mt-2 text-3xl font-semibold">{{ bookingsDashboard.bookingsToday }}</p>
        <p class="mt-3 text-sm font-medium text-wego-muted">Paid this month</p>
        <p v-if="bookingsDashboard.paidRevenueThisMonth.length === 0" class="mt-1 text-sm text-wego-muted">Nothing yet.</p>
        <p v-for="revenue in bookingsDashboard.paidRevenueThisMonth" :key="revenue.currencyCode" class="mt-1 text-lg font-semibold">
          {{ revenue.amount }} {{ revenue.currencyCode }}
        </p>
      </WegoPanel>

      <WegoPanel v-if="offeringsDashboard">
        <p class="text-sm font-medium text-wego-muted">Coming up (next 7 days)</p>
        <p v-if="offeringsDashboard.upcomingTrips.length === 0" class="mt-2 text-sm text-wego-muted">Nothing scheduled.</p>
        <ul v-else class="mt-2 space-y-2">
          <li v-for="trip in offeringsDashboard.upcomingTrips.slice(0, 5)" :key="trip.id" class="text-sm">
            <span class="font-semibold">{{ trip.title }}</span> · {{ trip.startsOn }}
          </li>
        </ul>
      </WegoPanel>

      <WegoPanel v-if="diversDashboard">
        <p class="text-sm font-medium text-wego-muted">Active divers</p>
        <p class="mt-2 text-3xl font-semibold">{{ diversDashboard.activeDivers }}</p>
      </WegoPanel>

      <WegoPanel v-if="equipmentDashboard">
        <p class="text-sm font-medium text-wego-muted">Equipment status</p>
        <p class="mt-2 text-sm">Active: <span class="font-semibold">{{ equipmentDashboard.active }}</span></p>
        <p class="mt-1 text-sm">In maintenance: <span class="font-semibold">{{ equipmentDashboard.inMaintenance }}</span></p>
        <p class="mt-1 text-sm">Retired: <span class="font-semibold">{{ equipmentDashboard.retired }}</span></p>
      </WegoPanel>
    </div>
    <p
      v-if="!bookingsDashboard && !offeringsDashboard && !diversDashboard && !equipmentDashboard && !dashboardError"
      class="mt-4 text-sm text-wego-muted"
    >
      Your account doesn't hold permission to view any business summary widget yet.
    </p>
  </section>
</template>
