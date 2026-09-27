<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoButton } from "@wego/ui";
import {
  clearAuthSession,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";

useHead({ title: "Notifications · Safari Tours Sharm" });

const router  = useRouter();
const session = ref<AuthSession | null>(null);

type NotifKind = "booking_new" | "booking_expired" | "payment_confirmed" | "slot_low";

interface Notification {
  id: string;
  kind: NotifKind;
  title: string;
  body: string;
  reference?: string;
  isRead: boolean;
  createdAt: string;
}

// Static placeholder notifications — real data from Phase 5 push/webhook system
const notifications = ref<Notification[]>([
  {
    id: "1",
    kind: "booking_new",
    title: "New booking received",
    body: "A new booking STR-2026-1 has been created and is awaiting payment confirmation.",
    reference: "STR-2026-1",
    isRead: false,
    createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
  },
  {
    id: "2",
    kind: "slot_low",
    title: "Slot almost full",
    body: "Desert Quad Bike — Morning slot on 2026-09-28 has only 2 seats remaining.",
    isRead: false,
    createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
  },
  {
    id: "3",
    kind: "payment_confirmed",
    title: "Payment confirmed",
    body: "Booking STR-2026-1 payment has been confirmed via Paymob.",
    reference: "STR-2026-1",
    isRead: true,
    createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
  },
  {
    id: "4",
    kind: "booking_expired",
    title: "Booking expired",
    body: "Booking STR-2026-2 expired after 30 minutes without payment. Seat released.",
    reference: "STR-2026-2",
    isRead: true,
    createdAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(),
  },
]);

const unreadCount = computed(() => notifications.value.filter((n) => !n.isRead).length);
const filterUnread = ref(false);

const displayed = computed(() =>
  filterUnread.value ? notifications.value.filter((n) => !n.isRead) : notifications.value,
);

const kindIcon: Record<NotifKind, string> = {
  booking_new:       "🔔",
  booking_expired:   "⏰",
  payment_confirmed: "✅",
  slot_low:          "⚠️",
};

const kindBadgeClass: Record<NotifKind, string> = {
  booking_new:       "badge-NEW",
  booking_expired:   "badge-EXPIRED",
  payment_confirmed: "badge-CONFIRMED",
  slot_low:          "badge-EXPIRED",
};

function kindLabel(kind: NotifKind): string {
  return {
    booking_new:       "New booking",
    booking_expired:   "Expired",
    payment_confirmed: "Payment",
    slot_low:          "Slot alert",
  }[kind];
}

function timeAgo(iso: string): string {
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (diff < 60)   return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return new Date(iso).toLocaleDateString();
}

function markAllRead() {
  notifications.value = notifications.value.map((n) => ({ ...n, isRead: true }));
}

function markRead(id: string) {
  const n = notifications.value.find((x) => x.id === id);
  if (n) n.isRead = true;
}

function logout() {
  clearAuthSession();
  void router.replace("/login");
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
});
</script>

<template>
  <main class="min-h-screen bg-sts-canvas px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-3xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <NuxtLink to="/" class="text-sm text-sts-muted hover:text-sts-ocean">← Overview</NuxtLink>
          <h1 class="mt-1 flex items-center gap-2 text-2xl font-semibold tracking-tight">
            Notifications
            <span
              v-if="unreadCount > 0"
              class="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-sts-sunset px-1.5 text-xs font-bold text-white tabular-nums"
            >
              {{ unreadCount }}
            </span>
          </h1>
        </div>
        <WegoButton type="button" variant="secondary" size="sm" class="text-sts-muted" @click="logout">
          Sign out
        </WegoButton>
      </header>

      <!-- Controls -->
      <div class="mt-6 flex flex-wrap items-center gap-3">
        <label class="flex cursor-pointer items-center gap-2 text-sm">
          <input
            v-model="filterUnread"
            type="checkbox"
            class="h-4 w-4 rounded border-sts-border accent-sts-ocean"
          >
          Show unread only
        </label>
        <WegoButton
          v-if="unreadCount > 0"
          type="button"
          variant="secondary"
          size="sm"
          @click="markAllRead"
        >
          Mark all as read
        </WegoButton>
        <span class="ms-auto text-xs text-sts-muted">
          {{ unreadCount }} unread of {{ notifications.length }}
        </span>
      </div>

      <!-- Coming-soon banner (real-time push planned in Phase 5) -->
      <div class="mt-4 rounded-2xl border border-sts-gold bg-sts-gold-soft px-5 py-3 text-sm">
        <span class="font-semibold text-sts-ocean">Phase 5 — </span>
        <span class="text-sts-muted">Real-time push notifications via WhatsApp + in-app websocket will replace the sample data shown here.</span>
      </div>

      <!-- Notification list -->
      <ul class="mt-4 space-y-3" role="list" aria-label="Notifications">
        <li
          v-for="n in displayed"
          :key="n.id"
          class="flex gap-4 rounded-2xl border bg-sts-surface px-5 py-4 shadow-sm transition-colors"
          :class="n.isRead ? 'border-sts-border opacity-70' : 'border-sts-ocean/30'"
          @click="markRead(n.id)"
        >
          <!-- Icon -->
          <div class="mt-0.5 shrink-0 text-xl">{{ kindIcon[n.kind] }}</div>

          <!-- Content -->
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <span class="font-semibold text-sm">{{ n.title }}</span>
                <span :class="`badge ${kindBadgeClass[n.kind]}`">{{ kindLabel(n.kind) }}</span>
                <span
                  v-if="!n.isRead"
                  class="inline-block h-2 w-2 rounded-full bg-sts-sunset"
                  aria-label="Unread"
                />
              </div>
              <time class="shrink-0 text-xs text-sts-muted tabular-nums" :datetime="n.createdAt">
                {{ timeAgo(n.createdAt) }}
              </time>
            </div>
            <p class="mt-1 text-sm text-sts-muted">{{ n.body }}</p>
            <NuxtLink
              v-if="n.reference"
              :to="`/bookings?ref=${n.reference}`"
              class="ref mt-1.5 inline-block text-xs font-semibold text-sts-ocean hover:underline underline-offset-2"
              @click.stop
            >
              {{ n.reference }} →
            </NuxtLink>
          </div>
        </li>

        <li v-if="displayed.length === 0" class="rounded-2xl border border-sts-border bg-sts-surface px-6 py-10 text-center shadow-sm">
          <p class="text-2xl mb-2">🔕</p>
          <p class="font-semibold text-sts-ink">All caught up</p>
          <p class="mt-1 text-sm text-sts-muted">No unread notifications.</p>
        </li>
      </ul>

    </div>
  </main>
</template>
