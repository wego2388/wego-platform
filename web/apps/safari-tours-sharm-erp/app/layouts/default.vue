<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { hasPermission, logoutAuthSession, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { getPublicSalesStatus } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import { isLocalizedErpRoute } from "../utils/erpLocale";
import ErpLanguageSwitch from "../components/ErpLanguageSwitch.vue";
import StaffNavigation from "../components/StaffNavigation.vue";

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
const menuButton = ref<HTMLButtonElement | null>(null);
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
const onMenuKey = (event: KeyboardEvent) => {
  if (event.key !== "Escape" || event.defaultPrevented || !menuOpen.value) return;
  event.preventDefault();
  closeMenu();
};
onMounted(() => {
  session.value = readAuthSession();
  void refreshSalesStatus();
  window.addEventListener("sts:sales-control-changed", onSalesChanged);
  window.addEventListener("keydown", onMenuKey);
});
onBeforeUnmount(() => {
  window.removeEventListener("sts:sales-control-changed", onSalesChanged);
  window.removeEventListener("keydown", onMenuKey);
});
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
    { to: "/categories", label: t("nav.categories"), show: hasPermission(session.value, "tours-operator.tour:view") },
    { to: "/suppliers", label: t("nav.suppliers"), show: hasPermission(session.value, "tours-operator.supplier:manage") },
    { to: "/drivers", label: t("nav.drivers"), show: hasPermission(session.value, "tours-operator.fleet:manage") },
    { to: "/vehicles", label: t("nav.vehicles"), show: hasPermission(session.value, "tours-operator.fleet:manage") },
    { to: "/finance", label: t("nav.finance"), show: hasPermission(session.value, "tours-operator.payment:view") },
    { to: "/profitability", label: t("nav.profitability"), show: hasPermission(session.value, "tours-operator.payment:view") },
    { to: "/costs", label: t("nav.costs"), show: hasPermission(session.value, "tours-operator.cost:manage") || hasPermission(session.value, "tours-operator.payment:view") },
    {
      to: "/settlements", label: t("nav.settlements"),
      show: ["tours-operator.settlement:pay", "tours-operator.settlement:approve", "tours-operator.payment:view"].some((p) => hasPermission(session.value, p)),
    },
    {
      to: "/cash-box", label: t("nav.cashBox"),
      show: ["tours-operator.cash-box:close", "tours-operator.cash-box:confirm", "tours-operator.payment:view"].some((p) => hasPermission(session.value, p)),
    },
    { to: "/customers", label: t("nav.customers"), show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/reviews", label: t("nav.reviews"), show: hasPermission(session.value, "tours-operator.booking:view") },
    { to: "/notifications", label: t("nav.messages"), show: hasPermission(session.value, "tours-operator.notification:manage") },
    { to: "/staff", label: t("nav.staff"), show: hasPermission(session.value, "identity:user-view") || hasPermission(session.value, "identity:role-view") },
    { to: "/sales", label: t("nav.sales"), show: hasPermission(session.value, "tours-operator.tour:manage") },
    { to: "/settings", label: t("nav.settings"), show: hasPermission(session.value, "tours-operator.settings:manage") },
  ].filter((link) => link.show),
);

function closeMenu() {
  if (!menuOpen.value) return;
  menuOpen.value = false;
  menuButton.value?.focus();
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
  <div class="staff-workspace min-h-screen bg-sts-canvas text-sts-ink">
    <a href="#main" class="sr-only focus:not-sr-only focus:fixed focus:start-3 focus:top-3 focus:z-50 focus:rounded-lg focus:bg-sts-surface focus:px-4 focus:py-2">{{ t('shell.skip') }}</a>
    <header class="sticky top-0 z-40 border-b border-white/10 bg-sts-ocean text-white print:hidden">
      <div class="mx-auto flex max-w-7xl flex-wrap items-center gap-2 px-4 py-3 sm:gap-4 sm:px-8">
        <NuxtLink to="/" class="flex shrink-0 items-center gap-2 font-semibold">
          <img src="/logo-mark.webp" alt="Safari Tours Sharm" width="46" height="38" class="h-9 w-auto" decoding="async">
          <span class="hidden sm:inline" aria-hidden="true">Safari Tours Sharm <span class="block text-xs font-normal text-white/75">{{ t('workspace.controlRoom') }}</span></span>
        </NuxtLink>
        <div class="ms-auto flex max-w-full flex-wrap items-center gap-1 sm:gap-2">
          <ErpLanguageSwitch />
          <template v-if="session">
          <span class="hidden max-w-40 truncate text-xs text-white/70 md:inline-block" :title="session.email">{{ session.email }}</span>
          <button type="button" class="rounded-lg border border-white/25 px-2 py-1.5 text-sm font-semibold hover:bg-white/10 sm:px-3" @click="signOut">{{ t('shell.signOut') }}</button>
          <button
            ref="menuButton"
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
      <div v-if="session && menuOpen" id="staff-menu" class="max-h-[calc(100dvh-5rem)] overflow-y-auto border-b border-sts-border bg-sts-surface px-4 py-5 text-sts-ink shadow-xl xl:hidden">
        <StaffNavigation :links="links" search-id="staff-mobile-search" />
      </div>
    </header>
    <div class="staff-frame" :class="session ? 'xl:grid xl:grid-cols-[15rem_minmax(0,1fr)]' : ''">
    <aside v-if="session" class="hidden border-e border-sts-border bg-sts-surface xl:block print:hidden">
      <div class="sticky top-20 max-h-[calc(100dvh-5rem)] overflow-y-auto px-3 py-6">
        <StaffNavigation :links="links" search-id="staff-desktop-search" />
      </div>
    </aside>
    <div class="min-w-0">
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
    </div>
  </div>
</template>
