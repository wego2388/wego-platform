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
  listBookings,
  listAllStaffTours,
  addMoney,
  type Booking,
  type Tour,
  ToursApiError,
} from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageKey } from "../utils/erpLocale";

const { t, count, money, dateLabel } = useErpLocale();
useHead(() => ({ title: `${t("nav.overview")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const bookings = ref<Booking[]>([]);
const toursById = ref<Record<string, Tour>>({});
const state = ref<"idle" | "loading" | "loaded" | "error">("idle");
const errorKey = ref<ErpMessageKey | null>(null);
const errorCode = ref("");
const errorMsg = computed(() => errorKey.value ? t(errorKey.value, { code: errorCode.value }) : "");

const canViewBookings = computed(() => hasPermission(session.value, "tours-operator.booking:view"));
const canViewTours    = computed(() => hasPermission(session.value, "tours-operator.tour:view"));
const workflow = computed(() => [
  { to: "/tours", title: "workspace.prepare", hint: "workspace.prepareHint", show: canViewTours.value },
  { to: "/bookings/new", title: "workspace.book", hint: "workspace.bookHint", show: hasPermission(session.value, "tours-operator.booking:create-office") },
  { to: "/today", title: "workspace.run", hint: "workspace.runHint", show: canViewBookings.value },
  { to: "/suppliers", title: "workspace.partners", hint: "workspace.partnersHint", show: hasPermission(session.value, "tours-operator.supplier:manage") },
  { to: "/finance", title: "workspace.accounts", hint: "workspace.accountsHint", show: hasPermission(session.value, "tours-operator.payment:view") },
  { to: "/settlements", title: "workspace.close", hint: "workspace.closeHint", show: ["tours-operator.payment:view", "tours-operator.settlement:pay", "tours-operator.settlement:approve"].some(permission => hasPermission(session.value, permission)) },
].filter(step => step.show) as { to: string; title: ErpMessageKey; hint: ErpMessageKey; show: boolean }[]);

// The operator's calendar day; Cairo's timezone rules include seasonal offsets.
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
  const tour = toursById.value[tourId];
  return tour?.nameEn ?? tour?.slug ?? tourId;
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

function errorText(err: unknown): ErpMessageKey {
  if (err instanceof ToursApiError) {
    if (err.status === 401) return "common.sessionExpired";
    if (err.status === 403) return "common.forbidden";
    return "common.requestFailed";
  }
  return "common.connectionFailed";
}

async function load() {
  if (!session.value) return;
  state.value = "loading";
  errorKey.value = null;
  errorCode.value = "";
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
    errorKey.value = errorText(err);
    errorCode.value = err instanceof ToursApiError ? err.errorCode : "";
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
  <main class="px-4 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">

      <header>
        <p class="text-sm font-semibold tracking-widest text-sts-muted uppercase">Safari Tours Sharm</p>
        <h1 class="mt-2 text-3xl font-semibold tracking-tight">{{ t('nav.overview') }}</h1>
        <p class="mt-3 max-w-2xl text-sm leading-6 text-sts-muted">{{ t('workspace.intro') }}</p>
      </header>

      <section v-if="session" aria-labelledby="workflow-heading" class="mt-7 rounded-3xl border border-sts-border bg-sts-surface p-5 shadow-sm sm:p-6">
        <h2 id="workflow-heading" class="text-xl font-semibold">{{ t('workspace.workflow') }}</h2>
        <p class="mt-2 text-xs leading-5 text-sts-muted">{{ t('workspace.workflowNote') }}</p>
        <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          <NuxtLink v-for="step in workflow" :key="step.title" :to="step.to" class="group rounded-2xl border border-sts-border bg-sts-canvas/50 p-4 transition-colors hover:border-sts-ocean-mid hover:bg-sts-info-soft">
            <p class="font-semibold text-sts-ocean">{{ t(step.title) }}</p>
            <p class="mt-2 text-sm leading-6 text-sts-muted">{{ t(step.hint) }}</p>
          </NuxtLink>
        </div>
      </section>

      <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ errorMsg }}</WegoAlert>

      <!-- KPI cards -->
      <section v-if="state !== 'idle'" aria-labelledby="kpi-heading" class="mt-8">
        <h2 id="kpi-heading" class="sr-only">{{ t('overview.metrics') }}</h2>
        <p class="mb-3 text-sm text-sts-muted">{{ t('overview.sampleNote') }}</p>
        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <article
            v-for="kpi in [
              { label: t('overview.todayBookings'), value: state === 'loaded' ? count(todayBookings.length) : '…', sub: t('overview.todayTours') },
              { label: t('overview.todayValue'), value: state === 'loaded' ? money(todayRevenue) : '…', sub: t('overview.valueNote') },
              { label: t('overview.pending'), value: state === 'loaded' ? count(pendingConfirm) : '…', sub: t('overview.pendingNote') },
              { label: t('overview.upcoming'), value: state === 'loaded' ? count(upcomingCount) : '…', sub: t('overview.upcomingNote') },
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
          <h2 class="text-xl font-semibold">{{ t('overview.recent') }}</h2>
          <NuxtLink to="/bookings" class="text-sm font-semibold text-sts-ocean hover:underline">
            {{ t('overview.viewAll') }}
          </NuxtLink>
        </div>

        <p v-if="state === 'loading'" class="mt-4 text-sm text-sts-muted">{{ t('common.loading') }}</p>
        <p v-else-if="!canViewBookings" class="mt-4 text-sm text-sts-muted">
          {{ t('overview.noPermission') }}
        </p>
        <p v-else-if="state === 'loaded' && recentBookings.length === 0" class="mt-4 text-sm text-sts-muted">
          {{ t('overview.empty') }}
        </p>

        <div v-else-if="recentBookings.length > 0" class="mt-4 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface shadow-sm">
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-sts-border bg-sts-canvas/60">
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.ref') }}</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.customer') }}</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.tour') }}</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.date') }}</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.pax') }}</th>
                  <th scope="col" class="px-4 py-3 text-end text-xs font-semibold text-sts-muted">{{ t('common.total') }}</th>
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">{{ t('common.status') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="b in recentBookings"
                  :key="b.id"
                  class="border-b border-sts-border/50 last:border-0 hover:bg-sts-canvas/50"
                >
                  <td class="ref px-5 py-3.5 font-mono text-xs text-sts-muted"><NuxtLink :to="`/bookings/${b.id}`" class="font-semibold text-sts-ocean hover:underline">{{ b.reference }}</NuxtLink></td>
                  <td class="px-4 py-3.5 font-medium">{{ b.customer.fullName }}</td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ tourName(b.tourId) }}</td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ dateLabel(b.tourDate) }}</td>
                  <td class="px-4 py-3.5">{{ count(b.adultsCount + b.childrenCount) }}</td>
                  <td class="money px-4 py-3.5 text-end font-semibold">{{ money(b.totalPrice) }}</td>
                  <td class="px-5 py-3.5">
                    <span :class="`badge badge-${b.status}`">{{ t(`status.${b.status}`) }}</span>
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
