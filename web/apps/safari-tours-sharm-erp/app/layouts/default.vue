<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { hasPermission, logoutAuthSession, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { getPublicSalesStatus } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import { isLocalizedErpRoute } from "../utils/erpLocale";
import ErpLanguageSwitch from "../components/ErpLanguageSwitch.vue";

/**
 * One staff shell for every page: brand, permission-aware navigation and the
 * signed-in user. The session lives in browser storage, so the navigation is
 * filled in after mount; pages keep their own auth redirects.
 */
const route = useRoute();
const router = useRouter();
const { locale, t } = useErpLocale();
const session = ref<AuthSession | null>(null);
const menuOpen = ref(false);
const signOutFailed = ref(false);

// Every staff member sees when online sales are paused, so nobody wonders
// why the website stopped taking bookings.
const salesPaused = ref(false);
const enquiryOnly = ref(false);
async function refreshSalesStatus() {
  try {
    const status = await getPublicSalesStatus();
    enquiryOnly.value = status.bookingMode === "ENQUIRY_ONLY";
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
    { to: "/", label: t("nav.overview"), show: true },
    { to: "/today", label: t("nav.today"), show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/bookings", label: t("nav.bookings"), show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/tours", label: t("nav.tours"), show: hasPermission(session.value, "tours-operator.tour:view") },
    { to: "/finance", label: t("nav.finance"), show: hasPermission(session.value, "tours-operator.payment:view") },
    { to: "/customers", label: t("nav.customers"), show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/reviews", label: t("nav.reviews"), show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/notifications", label: t("nav.messages"), show: hasPermission(session.value, "tours-operator.notification:manage") },
    { to: "/staff", label: t("nav.staff"), show: hasPermission(session.value, "identity:user-view") || hasPermission(session.value, "identity:role-view") },
    { to: "/sales", label: t("nav.sales"), show: hasPermission(session.value, "tours-operator.tour:manage") },
    { to: "/settings", label: t("nav.settings"), show: hasPermission(session.value, "tours-operator.settings:manage") },
  ].filter((link) => link.show),
);

function isActive(to: string) {
  return to === "/" ? route.path === "/" : route.path === to || route.path.startsWith(`${to}/`);
}

async function signOut() {
  signOutFailed.value = false;
  try {
    await logoutAuthSession(session.value);
  } catch {
    // The token could not be revoked: keep the session visible and say so,
    // rather than pretending the staff member is signed out.
    signOutFailed.value = true;
    return;
  }
  session.value = null;
  void router.replace("/login");
}
</script>

<template>
  <div class="min-h-screen bg-sts-canvas text-sts-ink">
    <a href="#main" class="sr-only focus:not-sr-only focus:fixed focus:start-3 focus:top-3 focus:z-50 focus:rounded-lg focus:bg-sts-surface focus:px-4 focus:py-2">{{ t('shell.skip') }}</a>
    <header class="sticky top-0 z-40 border-b border-white/10 bg-sts-ocean text-white print:hidden">
      <div class="mx-auto flex max-w-7xl flex-wrap items-center gap-2 px-4 py-3 sm:gap-4 sm:px-8">
        <NuxtLink to="/" class="flex shrink-0 items-center gap-2 font-semibold">
          <img src="/logo-mark.webp" alt="Safari Tours Sharm" width="46" height="38" class="h-9 w-auto" decoding="async">
          <span class="hidden sm:inline xl:hidden" aria-hidden="true">Safari Tours Sharm · {{ t('shell.staff') }}</span>
        </NuxtLink>
        <nav v-if="session" class="hidden min-w-0 flex-1 items-center gap-1 overflow-x-auto xl:flex" :aria-label="t('shell.navigation')">
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
        <div class="ms-auto flex max-w-full flex-wrap items-center gap-1 sm:gap-2">
          <ErpLanguageSwitch />
          <template v-if="session">
          <span class="hidden max-w-40 truncate text-xs text-white/70 md:inline-block" :title="session.email">{{ session.email }}</span>
          <button type="button" class="rounded-lg border border-white/25 px-2 py-1.5 text-sm font-semibold hover:bg-white/10 sm:px-3" @click="signOut">{{ t('shell.signOut') }}</button>
          <button
            type="button"
            class="rounded-lg border border-white/25 px-2 py-1.5 text-sm font-semibold hover:bg-white/10 sm:px-3 xl:hidden"
            :aria-expanded="menuOpen"
            aria-controls="staff-menu"
            @click="menuOpen = !menuOpen"
          >
            {{ t('shell.menu') }}
          </button>
          </template>
        </div>
      </div>
      <nav v-if="session && menuOpen" id="staff-menu" class="grid gap-1 border-t border-white/10 px-4 py-3 xl:hidden" :aria-label="t('shell.navigation')">
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
    <p v-if="session && enquiryOnly" role="status" class="border-b border-sts-border bg-sts-info-soft px-4 py-3 text-sm text-sts-info print:hidden">{{ t('sales.enquiryMode') }}</p>
    <p v-else-if="session && salesPaused" role="alert" class="bg-sts-danger px-4 py-2 text-center text-sm font-semibold text-white print:hidden">
      {{ t('shell.salesPaused') }}
      <NuxtLink to="/sales" class="underline">{{ t('shell.salesSwitch') }}</NuxtLink>
    </p>
    <p v-if="signOutFailed" role="alert" class="bg-sts-danger-soft px-4 py-2 text-center text-sm font-semibold text-sts-danger">{{ t('shell.signOutFailed') }}</p>
    <p v-if="locale === 'ar' && !isLocalizedErpRoute(route.path)" role="status" class="border-b border-sts-border bg-sts-info-soft px-4 py-3 text-sm text-sts-info print:hidden">{{ t('shell.englishPage') }}</p>
    <div id="main" tabindex="-1">
      <slot />
    </div>
  </div>
</template>
