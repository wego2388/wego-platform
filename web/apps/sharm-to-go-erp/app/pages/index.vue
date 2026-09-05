<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoPageHeader, WegoPanel } from "@wego/ui";
import { type AuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  type Category,
  listCategories,
  listProviders,
  listServices,
  type Provider,
  type Service,
  type ServiceStatus,
} from "../composables/useTravelMarketplaceApi";

definePageMeta({ layout: "app-shell" });

useHead({
  title: "Dashboard · Sharm To Go",
  meta: [{ name: "description", content: "Sharm To Go staff operations dashboard." }],
});

const session = ref<AuthSession | null>(null);
const services = ref<Service[] | null>(null);
const categories = ref<Category[] | null>(null);
const providers = ref<Provider[] | null>(null);
const dashboardError = ref("");

const statusCounts = computed(() => {
  const counts: Record<ServiceStatus, number> = { DRAFT: 0, REVIEW: 0, APPROVED: 0, PUBLISHED: 0, SUSPENDED: 0, ARCHIVED: 0 };
  for (const service of services.value ?? []) counts[service.status] += 1;
  return counts;
});

async function loadDashboard() {
  if (!session.value) return;
  const token = session.value.token;
  dashboardError.value = "";
  try {
    const tasks: Promise<void>[] = [];
    if (hasPermission(session.value, "service:view")) {
      tasks.push(listServices(token, { size: 50 }).then((data) => void (services.value = data)));
    }
    if (hasPermission(session.value, "provider:view")) {
      tasks.push(listProviders(token, { status: "ACTIVE", size: 50 }).then((data) => void (providers.value = data)));
    }
    // Categories have no dedicated permission — service:view/manage
    // already gates the whole catalog surface, matching
    // ServiceQueryService's own real authorization.
    if (hasPermission(session.value, "service:view")) {
      tasks.push(listCategories(token, { status: "ACTIVE" }).then((data) => void (categories.value = data)));
    }
    await Promise.all(tasks);
  } catch {
    dashboardError.value = "Could not load the live catalog summary. Check your connection and try again.";
  }
}

onMounted(() => {
  session.value = readAuthSession();
  loadDashboard();
});
</script>

<template>
  <WegoPageHeader eyebrow="Sharm To Go" title="Dashboard" description="Today's real catalog state." />

  <p v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6 text-wego-muted">
    You need to sign in to view the dashboard.
  </p>

  <section v-else aria-labelledby="live-summary" class="mt-10">
    <h2 id="live-summary" class="text-xl font-semibold">Live catalog summary</h2>
    <WegoAlert v-if="dashboardError" variant="danger" class="mt-4">{{ dashboardError }}</WegoAlert>

    <div class="mt-6 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
      <WegoPanel v-if="services" title="Services by status">
        <div class="flex flex-wrap gap-2">
          <WegoBadge v-if="statusCounts.PUBLISHED > 0" tone="success">{{ statusCounts.PUBLISHED }} Published</WegoBadge>
          <WegoBadge v-if="statusCounts.APPROVED > 0" tone="accent">{{ statusCounts.APPROVED }} Approved</WegoBadge>
          <WegoBadge v-if="statusCounts.REVIEW > 0" tone="warning">{{ statusCounts.REVIEW }} In review</WegoBadge>
          <WegoBadge v-if="statusCounts.DRAFT > 0" tone="neutral">{{ statusCounts.DRAFT }} Draft</WegoBadge>
          <WegoBadge v-if="statusCounts.SUSPENDED > 0" tone="danger">{{ statusCounts.SUSPENDED }} Suspended</WegoBadge>
          <WegoBadge v-if="statusCounts.ARCHIVED > 0" tone="neutral">{{ statusCounts.ARCHIVED }} Archived</WegoBadge>
        </div>
        <p v-if="services.length === 0" class="mt-2 text-sm text-wego-muted">No services yet.</p>
        <NuxtLink to="/services" class="mt-4 inline-block text-sm font-semibold text-wego-accent underline">Manage services</NuxtLink>
      </WegoPanel>

      <WegoPanel v-if="categories" title="Categories">
        <p class="text-3xl font-semibold">{{ categories.length }}</p>
        <p class="mt-1 text-sm text-wego-muted">active</p>
        <NuxtLink to="/categories" class="mt-4 inline-block text-sm font-semibold text-wego-accent underline">Manage categories</NuxtLink>
      </WegoPanel>

      <WegoPanel v-if="providers" title="Providers">
        <p class="text-3xl font-semibold">{{ providers.length }}</p>
        <p class="mt-1 text-sm text-wego-muted">active</p>
        <NuxtLink to="/providers" class="mt-4 inline-block text-sm font-semibold text-wego-accent underline">Manage providers</NuxtLink>
      </WegoPanel>
    </div>

    <p v-if="!services && !categories && !providers && !dashboardError" class="mt-4 text-sm text-wego-muted">
      Your account doesn't hold permission to view any catalog summary widget yet.
    </p>

    <WegoPanel v-if="services && statusCounts.PUBLISHED === 0" class="mt-6" title="No real service is live yet">
      <p class="text-sm text-wego-muted">
        The public website and mobile app both correctly show an honest empty state until a real, owner-approved service is
        published — this is the real current truth, not a defect. See
        <span class="font-mono">clients/sharm-to-go/content-research/SHARM_TO_GO_SERVICE_INTAKE_SHEETS.md</span> for the
        launch-ready draft catalog.
      </p>
    </WegoPanel>
  </section>
</template>
