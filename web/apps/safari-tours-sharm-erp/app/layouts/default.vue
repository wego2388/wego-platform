<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { hasPermission, logoutAuthSession, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { getPublicSalesStatus } from "../composables/useToursApi";

/**
 * One staff shell for every page: brand, permission-aware navigation and the
 * signed-in user. The session lives in browser storage, so the navigation is
 * filled in after mount; pages keep their own auth redirects.
 */
const route = useRoute();
const router = useRouter();
const session = ref<AuthSession | null>(null);
const menuOpen = ref(false);
const signOutError = ref("");

// Every staff member sees when online sales are paused, so nobody wonders
// why the website stopped taking bookings.
const salesPaused = ref(false);
async function refreshSalesStatus() {
  try {
    const status = await getPublicSalesStatus();
    salesPaused.value = !status.bookingsOpen || !status.paymentsOpen;
  } catch {
    // Unknown is not "paused": keep the last known state.
  }
}

const onSalesChanged = () => void refreshSalesStatus();
onMounted(() => {
  session.value = readAuthSession();
  void refreshSalesStatus();
  window.addEventListener("sts:sales-control-changed", onSalesChanged);
});
onBeforeUnmount(() => window.removeEventListener("sts:sales-control-changed", onSalesChanged));
watch(() => route.fullPath, () => {
  session.value = readAuthSession();
  menuOpen.value = false;
  void refreshSalesStatus();
});

const links = computed(() =>
  [
    { to: "/", label: "Overview", show: true },
    { to: "/today", label: "Today", show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/bookings", label: "Bookings", show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/tours", label: "Tours", show: hasPermission(session.value, "tours-operator.tour:view") },
    { to: "/finance", label: "Finance", show: hasPermission(session.value, "tours-operator.payment:view") },
    { to: "/customers", label: "Customers", show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/reviews", label: "Reviews", show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/notifications", label: "Messages", show: hasPermission(session.value, "tours-operator.notification:manage") },
    { to: "/staff", label: "Staff", show: hasPermission(session.value, "identity:user-view") || hasPermission(session.value, "identity:role-view") },
    { to: "/sales", label: "Online sales", show: hasPermission(session.value, "tours-operator.tour:manage") },
    { to: "/settings", label: "Settings", show: hasPermission(session.value, "tours-operator.settings:manage") },
  ].filter((link) => link.show),
);

function isActive(to: string) {
  return to === "/" ? route.path === "/" : route.path === to || route.path.startsWith(`${to}/`);
}

async function signOut() {
  signOutError.value = "";
  try {
    await logoutAuthSession(session.value);
  } catch {
    // The token could not be revoked: keep the session visible and say so,
    // rather than pretending the staff member is signed out.
    signOutError.value = "Sign out failed — the server could not end this session. Try again.";
    return;
  }
  session.value = null;
  void router.replace("/login");
}
</script>

<template>
  <div class="min-h-screen bg-sts-canvas text-sts-ink">
    <a href="#main" class="sr-only focus:not-sr-only focus:fixed focus:start-3 focus:top-3 focus:z-50 focus:rounded-lg focus:bg-sts-surface focus:px-4 focus:py-2">Skip to content</a>
    <header class="sticky top-0 z-40 border-b border-white/10 bg-sts-ocean text-white print:hidden">
      <div class="mx-auto flex max-w-7xl items-center gap-4 px-4 py-3 sm:px-8">
        <NuxtLink to="/" class="flex shrink-0 items-center gap-2 font-semibold">
          <span class="grid size-8 place-items-center rounded-lg bg-sts-gold text-xs font-black text-sts-ocean">STS</span>
          <span class="hidden sm:inline xl:hidden 2xl:inline">Safari Tours Sharm · Staff</span>
        </NuxtLink>
        <nav v-if="session" class="hidden flex-1 items-center gap-1 overflow-x-auto xl:flex" aria-label="Main navigation">
          <NuxtLink
            v-for="link in links"
            :key="link.to"
            :to="link.to"
            class="rounded-lg px-2.5 py-2 text-sm font-semibold whitespace-nowrap transition-colors"
            :class="isActive(link.to) ? 'bg-white/15 text-white' : 'text-white/75 hover:bg-white/10 hover:text-white'"
            :aria-current="isActive(link.to) ? 'page' : undefined"
          >
            {{ link.label }}
          </NuxtLink>
        </nav>
        <div v-if="session" class="ms-auto flex items-center gap-2">
          <span class="hidden text-xs text-white/70 md:inline xl:hidden" :title="session.email">{{ session.email }}</span>
          <button type="button" class="rounded-lg border border-white/25 px-3 py-1.5 text-sm font-semibold hover:bg-white/10" @click="signOut">Sign out</button>
          <button
            type="button"
            class="rounded-lg border border-white/25 px-3 py-1.5 text-sm font-semibold hover:bg-white/10 xl:hidden"
            :aria-expanded="menuOpen"
            aria-controls="staff-menu"
            @click="menuOpen = !menuOpen"
          >
            Menu
          </button>
        </div>
      </div>
      <nav v-if="session && menuOpen" id="staff-menu" class="grid gap-1 border-t border-white/10 px-4 py-3 xl:hidden" aria-label="Main navigation">
        <NuxtLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="rounded-lg px-3 py-2 text-sm font-semibold"
          :class="isActive(link.to) ? 'bg-white/15' : 'text-white/80'"
        >
          {{ link.label }}
        </NuxtLink>
      </nav>
    </header>
    <p v-if="session && salesPaused" role="alert" class="bg-sts-danger px-4 py-2 text-center text-sm font-semibold text-white print:hidden">
      Online sales are paused.
      <NuxtLink to="/sales" class="underline">Open the switch</NuxtLink>
    </p>
    <p v-if="signOutError" role="alert" class="bg-sts-danger-soft px-4 py-2 text-center text-sm font-semibold text-sts-danger">{{ signOutError }}</p>
    <div id="main" tabindex="-1">
      <slot />
    </div>
  </div>
</template>
