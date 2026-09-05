<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { readAuthSession } from "../composables/useAuthSession";
import { useTheme } from "../composables/useTheme";

// WEGO-014 Phase 4. Deliberately does NOT reimplement sign-out or any
// session/permission logic here — that stays exactly where it already
// is (login.vue's own careful best-effort-revocation logout). This
// shell only links to /login for the account area, and shows every
// nav group unconditionally (the same static-nav approach the old
// flat header already used) — a permission-filtered nav would be new
// authorization logic, a real Tier 1 trigger this packet deliberately
// stays under. Each destination page's own existing permission checks
// are unchanged and still the real gate.

interface NavLink {
  to: string;
  label: string;
}
interface NavGroup {
  label: string;
  links: NavLink[];
}

const navGroups: NavGroup[] = [
  { label: "Overview", links: [{ to: "/", label: "Dashboard" }] },
  {
    label: "Diving Operations",
    links: [
      { to: "/offerings", label: "Offerings" },
      { to: "/bookings", label: "Bookings" },
      { to: "/divers", label: "Divers" },
      { to: "/equipment", label: "Equipment & Tanks" },
      { to: "/boat-charters", label: "Boat Charters" },
      { to: "/course-enrollments", label: "Courses" },
    ],
  },
  {
    label: "People",
    links: [
      { to: "/employees", label: "Employees" },
      { to: "/attendance", label: "Attendance" },
      { to: "/leave-requests", label: "Leave Requests" },
    ],
  },
  {
    label: "Finance",
    links: [
      { to: "/chart-of-accounts", label: "Chart of Accounts" },
      { to: "/journal-entries", label: "Journal Entries" },
      { to: "/payroll", label: "Payroll" },
      { to: "/reports", label: "Reports" },
    ],
  },
  {
    label: "Administration",
    links: [
      { to: "/accounts", label: "Staff Accounts" },
      { to: "/roles", label: "Roles & Permissions" },
    ],
  },
];

const session = ref<ReturnType<typeof readAuthSession>>(null);
onMounted(() => {
  session.value = readAuthSession();
});

const { preference, setPreference } = useTheme();
function cycleTheme(): void {
  const order: Array<typeof preference.value> = ["system", "light", "dark"];
  setPreference(order[(order.indexOf(preference.value) + 1) % order.length]!);
}

// A single <nav>, always in the DOM — CSS alone repositions it between a
// permanent desktop sidebar and an off-canvas mobile drawer, so there is
// exactly one set of nav-link elements, never a duplicate hidden copy
// (which would make every getByRole/getByText nav assertion ambiguous).
const drawerOpen = ref(false);
const navRef = ref<HTMLElement | null>(null);
const toggleRef = ref<HTMLElement | null>(null);

function openDrawer(): void {
  drawerOpen.value = true;
}
function closeDrawer(): void {
  if (!drawerOpen.value) return;
  drawerOpen.value = false;
  toggleRef.value?.focus();
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape" && drawerOpen.value) closeDrawer();
}

onMounted(() => window.addEventListener("keydown", handleKeydown));
onBeforeUnmount(() => window.removeEventListener("keydown", handleKeydown));
</script>

<template>
  <div class="min-h-screen bg-wego-canvas text-wego-ink">
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-wego-toast focus:rounded-wego-control focus:bg-wego-accent focus:px-4 focus:py-2 focus:text-white"
    >
      Skip to content
    </a>

    <div
      v-if="drawerOpen"
      class="fixed inset-0 z-wego-overlay bg-wego-surface-overlay lg:hidden"
      @click="closeDrawer"
    />

    <header class="flex items-center justify-between border-b border-wego-border bg-wego-surface px-4 py-3 lg:hidden">
      <button
        ref="toggleRef"
        type="button"
        aria-controls="app-nav"
        :aria-expanded="drawerOpen"
        class="rounded-wego-control p-2 text-wego-ink hover:bg-wego-surface-hover"
        @click="openDrawer"
      >
        <span class="sr-only">Open navigation</span>
        <svg viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Wego Platform</p>
      <div class="w-10" />
    </header>

    <div class="lg:flex">
      <nav
        id="app-nav"
        ref="navRef"
        aria-label="Primary"
        class="fixed inset-y-0 left-0 z-wego-modal w-72 -translate-x-full overflow-y-auto border-r border-wego-border bg-wego-surface p-5 transition-transform duration-200 ease-[var(--wego-motion-easing-standard)] motion-reduce:transition-none lg:sticky lg:top-0 lg:z-auto lg:h-screen lg:w-64 lg:translate-x-0"
        :class="drawerOpen && 'translate-x-0'"
      >
        <p class="hidden text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase lg:block">Wego Platform</p>
        <div class="mt-6 space-y-6">
          <div v-for="group in navGroups" :key="group.label">
            <p class="px-2 text-xs font-semibold tracking-wide text-wego-muted uppercase">{{ group.label }}</p>
            <ul class="mt-1">
              <li v-for="link in group.links" :key="link.to">
                <NuxtLink
                  :to="link.to"
                  class="block rounded-wego-control px-2 py-2 text-sm font-medium text-wego-ink hover:bg-wego-surface-hover"
                  active-class="bg-wego-accent-soft text-wego-accent"
                  @click="closeDrawer"
                >
                  {{ link.label }}
                </NuxtLink>
              </li>
            </ul>
          </div>
        </div>

        <div class="mt-8 border-t border-wego-border pt-4">
          <button
            type="button"
            class="flex w-full items-center justify-between rounded-wego-control px-2 py-2 text-sm font-medium text-wego-ink hover:bg-wego-surface-hover"
            @click="cycleTheme"
          >
            <span>Theme</span>
            <span class="text-wego-muted capitalize">{{ preference }}</span>
          </button>
          <NuxtLink
            to="/login"
            class="mt-1 block truncate rounded-wego-control px-2 py-2 text-sm font-medium text-wego-ink hover:bg-wego-surface-hover"
          >
            {{ session?.email ?? "Account" }}
          </NuxtLink>
        </div>
      </nav>

      <main id="main-content" tabindex="-1" class="min-w-0 flex-1 px-6 py-10 sm:px-10 lg:px-12">
        <slot />
      </main>
    </div>
  </div>
</template>
