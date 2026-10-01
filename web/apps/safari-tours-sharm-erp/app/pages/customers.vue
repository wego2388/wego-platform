<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import {
  addMoney,
  formatMoney,
  listBookings,
  ToursApiError,
  type Booking,
  type Money,
} from "../composables/useToursApi";

useHead({ title: "Customers · Safari Tours Sharm" });

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const state   = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error   = ref("");

// Build customer list from bookings (unique by phone)
interface Customer {
  fullName: string;
  phone: string;
  nationality: string;
  email: string | null;
  bookingCount: number;
  lastBooking: string;  // ISO date
  totalSpent: Money;
}

const customers   = ref<Customer[]>([]);
const searchQuery = ref("");
const filterNat   = ref("");

const canView = computed(() => hasPermission(session.value, "tours-operator.booking:view"));

const filteredCustomers = computed(() => {
  let list = customers.value;
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase();
    list = list.filter(
      (c) =>
        c.fullName.toLowerCase().includes(q) ||
        c.phone.includes(q) ||
        (c.email ?? "").toLowerCase().includes(q),
    );
  }
  if (filterNat.value) {
    list = list.filter((c) => c.nationality === filterNat.value);
  }
  return list;
});

const nationalities = computed(() => [
  ...new Set(customers.value.map((c) => c.nationality).filter(Boolean)),
].sort());

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

function buildCustomers(bookings: Booking[]): Customer[] {
  const map = new Map<string, Customer>();
  for (const b of bookings) {
    const key = b.customer.phone;
    const existing = map.get(key);
    if (existing) {
      existing.bookingCount++;
      existing.totalSpent = addMoney([existing.totalSpent, b.totalPrice]);
      if (b.tourDate > existing.lastBooking) existing.lastBooking = b.tourDate;
    } else {
      map.set(key, {
        fullName: b.customer.fullName,
        phone: b.customer.phone,
        nationality: b.customer.nationality,
        email: b.customer.email,
        bookingCount: 1,
        lastBooking: b.tourDate,
        totalSpent: b.totalPrice,
      });
    }
  }
  return [...map.values()].sort((a, b) => b.lastBooking.localeCompare(a.lastBooking));
}

async function load() {
  if (!session.value || !canView.value) return;
  state.value = "loading";
  error.value = "";
  try {
    // Load all bookings — customers are derived from booking data
    const bookings = await listBookings(session.value.token, { size: 500 });
    customers.value = buildCustomers(bookings);
    state.value = "loaded";
  } catch (err) {
    handleApiError(err);
    error.value = err instanceof ToursApiError ? err.errorCode : "Failed to load customers.";
    state.value = "error";
  }
}


onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  void load();
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">Customers</h1>
        </div>
      </header>

      <!-- Nav -->

      <!-- Permission check -->
      <WegoAlert v-if="!canView" variant="danger" class="mt-6">
        You need <code>tours-operator.booking:view</code> permission to see customers.
      </WegoAlert>

      <template v-else>
        <!-- Filters -->
        <div class="mt-6 flex flex-wrap items-center gap-3">
          <input
            v-model="searchQuery"
            type="search"
            placeholder="Search by name, phone or email…"
            class="min-w-64 rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          >
          <select
            v-model="filterNat"
            aria-label="Filter by nationality"
            class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          >
            <option value="">All nationalities</option>
            <option v-for="nat in nationalities" :key="nat" :value="nat">{{ nat }}</option>
          </select>
          <span v-if="state === 'loaded'" class="text-sm text-sts-muted">
            {{ filteredCustomers.length }} customer{{ filteredCustomers.length !== 1 ? 's' : '' }}
          </span>
        </div>

        <!-- Error -->
        <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ error }}</WegoAlert>

        <!-- Loading -->
        <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted">Loading…</p>

        <!-- Empty -->
        <p v-else-if="state === 'loaded' && filteredCustomers.length === 0" class="mt-6 text-sm text-sts-muted">
          No customers found.
        </p>

        <!-- Table -->
        <div v-else-if="filteredCustomers.length > 0" class="mt-6 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface shadow-sm">
          <div class="overflow-x-auto">
            <table class="w-full text-sm" aria-label="Customers list">
              <thead>
                <tr class="border-b border-sts-border bg-sts-canvas/60">
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Name</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Phone</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Nationality</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">Email</th>
                  <th scope="col" class="px-4 py-3 text-center text-xs font-semibold text-sts-muted">Bookings</th>
                  <th scope="col" class="px-4 py-3 text-end   text-xs font-semibold text-sts-muted">Total spent</th>
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">Last booking</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="c in filteredCustomers"
                  :key="c.phone"
                  class="border-b border-sts-border/50 last:border-0 hover:bg-sts-canvas/50"
                >
                  <td class="px-5 py-3.5 font-medium">{{ c.fullName }}</td>
                  <td class="px-4 py-3.5">
                    <a :href="`tel:${c.phone}`" class="phone text-sts-ocean underline underline-offset-2 text-xs">
                      {{ c.phone }}
                    </a>
                  </td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ c.nationality }}</td>
                  <td class="px-4 py-3.5 text-sts-muted text-xs">{{ c.email ?? '—' }}</td>
                  <td class="px-4 py-3.5 text-center tabular-nums font-semibold text-sts-ocean">{{ c.bookingCount }}</td>
                  <td class="money px-4 py-3.5 text-end font-semibold">{{ formatMoney(c.totalSpent) }}</td>
                  <td class="px-5 py-3.5 text-sts-muted tabular-nums">{{ c.lastBooking }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Note: data is derived from bookings — no direct customers API yet -->
        <p class="mt-4 text-xs text-sts-muted">
          Customer data is derived from booking records.
        </p>
      </template>

    </div>
  </main>
</template>
