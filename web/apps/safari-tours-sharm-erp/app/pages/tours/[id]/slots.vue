<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../../../composables/useAuthSession";
import {
  getTour,
  listSlotsByRange,
  ToursApiError,
  type Tour,
  type TourSlot,
  type TimeSlot,
} from "../../../composables/useToursApi";

useHead({ title: "Tour Slots · Safari Tours Sharm" });

const route  = useRoute();
const router = useRouter();

const session   = ref<AuthSession | null>(null);
const tour      = ref<Tour | null>(null);
const slots     = ref<TourSlot[]>([]);
const loadState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const loadError = ref("");

// Calendar: navigate by week (Mon–Sun)
const today      = new Date();
const weekStart  = ref(getMonday(today));

const _canManage = computed(() => hasPermission(session.value, "tours-operator.slot:manage"));
const _canView   = computed(() => hasPermission(session.value, "tours-operator.tour:view"));

const tourId = computed(() => String(route.params.id));

// 7 days from weekStart
const weekDays = computed(() => {
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(weekStart.value);
    d.setDate(d.getDate() + i);
    return isoDate(d);
  });
});

const weekLabel = computed(() => {
  const from = weekDays.value[0];
  const to   = weekDays.value[6];
  return `${from} → ${to}`;
});

// Slots indexed by date+timeSlot for O(1) lookup
const slotIndex = computed(() => {
  const idx: Record<string, TourSlot> = {};
  for (const s of slots.value) {
    idx[`${s.date}__${s.timeSlot}`] = s;
  }
  return idx;
});

const TIME_SLOTS: TimeSlot[] = ["SUNRISE", "MORNING", "AFTERNOON", "SUNSET"];

function getMonday(d: Date): Date {
  const day = d.getDay(); // 0=Sun
  const diff = day === 0 ? -6 : 1 - day;
  const m = new Date(d);
  m.setDate(d.getDate() + diff);
  m.setHours(0, 0, 0, 0);
  return m;
}

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function prevWeek() {
  const d = new Date(weekStart.value);
  d.setDate(d.getDate() - 7);
  weekStart.value = d;
}

function nextWeek() {
  const d = new Date(weekStart.value);
  d.setDate(d.getDate() + 7);
  weekStart.value = d;
}

function dayLabel(iso: string): string {
  return new Date(iso).toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" });
}

function slotFor(date: string, time: TimeSlot): TourSlot | null {
  return slotIndex.value[`${date}__${time}`] ?? null;
}

function availabilityClass(slot: TourSlot | null): string {
  if (!slot) return "bg-sts-canvas text-sts-muted";
  if (slot.isBlocked) return "bg-sts-danger-soft text-sts-danger";
  if (slot.available === 0) return "bg-sts-warning-soft text-sts-warning";
  if (slot.available <= 3) return "bg-yellow-50 text-yellow-700";
  return "bg-sts-success-soft text-sts-success";
}

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  loadState.value = "loading";
  loadError.value = "";
  try {
    const [tourResult, slotsResult] = await Promise.all([
      getTour(session.value.token, tourId.value),
      listSlotsByRange(session.value.token, tourId.value, weekDays.value[0]!, weekDays.value[6]!),
    ]);
    tour.value      = tourResult;
    slots.value     = slotsResult;
    loadState.value = "loaded";
  } catch (e) {
    handleApiError(e);
    loadError.value = e instanceof ToursApiError ? e.errorCode : "Failed to load slots.";
    loadState.value = "error";
  }
}


watch(weekStart, () => { void load(); });

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  void load();
});
</script>

<template>
  <main class="px-4 py-8 text-sts-ink sm:px-8 lg:px-14">
    <div class="mx-auto max-w-7xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <NuxtLink to="/tours" class="text-sm text-sts-muted hover:text-sts-ocean">← Tours</NuxtLink>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">
            Slots
            <span v-if="tour" class="font-mono text-sts-ocean text-lg">{{ tour.slug }}</span>
          </h1>
        </div>
      </header>

      <!-- Nav -->

      <!-- Error -->
      <WegoAlert v-if="loadState === 'error'" variant="danger" class="mt-6">{{ loadError }}</WegoAlert>

      <!-- Week nav -->
      <div class="mt-6 flex items-center gap-4">
        <WegoButton type="button" variant="secondary" size="sm" @click="prevWeek">← Prev week</WegoButton>
        <span class="text-sm font-semibold text-sts-ocean tabular-nums">{{ weekLabel }}</span>
        <WegoButton type="button" variant="secondary" size="sm" @click="nextWeek">Next week →</WegoButton>
      </div>

      <!-- Legend -->
      <div class="mt-3 flex flex-wrap gap-3 text-xs">
        <span class="flex items-center gap-1.5"><span class="inline-block h-3 w-3 rounded bg-sts-success-soft"/>Available</span>
        <span class="flex items-center gap-1.5"><span class="inline-block h-3 w-3 rounded bg-yellow-50 border border-yellow-200"/>≤3 seats</span>
        <span class="flex items-center gap-1.5"><span class="inline-block h-3 w-3 rounded bg-sts-warning-soft"/>Full</span>
        <span class="flex items-center gap-1.5"><span class="inline-block h-3 w-3 rounded bg-sts-danger-soft"/>Blocked</span>
        <span class="flex items-center gap-1.5"><span class="inline-block h-3 w-3 rounded bg-sts-canvas border border-sts-border"/>No slot</span>
      </div>

      <!-- Loading -->
      <p v-if="loadState === 'loading'" class="mt-6 text-sm text-sts-muted">Loading…</p>

      <!-- Calendar grid -->
      <div v-else class="mt-4 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface shadow-sm">
        <table class="w-full text-sm border-collapse" aria-label="Weekly slot availability">
          <thead>
            <tr class="border-b border-sts-border bg-sts-canvas/60">
              <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted w-24">Time</th>
              <th
                v-for="day in weekDays"
                :key="day"
                scope="col"
                class="px-3 py-3 text-center text-xs font-semibold text-sts-muted"
                :class="day === isoDate(today) ? 'bg-sts-gold-soft' : ''"
              >
                {{ dayLabel(day) }}
                <span v-if="day === isoDate(today)" class="ms-1 text-sts-gold font-bold">●</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="timeSlot in TIME_SLOTS"
              :key="timeSlot"
              class="border-b border-sts-border/40 last:border-0"
            >
              <td class="px-4 py-3 text-xs font-semibold text-sts-muted whitespace-nowrap">
                {{ timeSlot }}
              </td>
              <td
                v-for="day in weekDays"
                :key="day"
                class="px-2 py-2 text-center"
                :class="day === isoDate(today) ? 'bg-sts-gold-soft/40' : ''"
              >
                <template v-if="slotFor(day, timeSlot)">
                  <div
                    class="rounded-lg px-2 py-1.5 text-xs font-semibold"
                    :class="availabilityClass(slotFor(day, timeSlot))"
                    :title="slotFor(day, timeSlot)?.isBlocked ? 'Blocked' : `${slotFor(day, timeSlot)?.available} of ${slotFor(day, timeSlot)?.capacity} places free (places = guests)`"
                  >
                    <template v-if="slotFor(day, timeSlot)?.isBlocked">Blocked</template>
                    <template v-else>
                      <span class="block tabular-nums">{{ slotFor(day, timeSlot)?.available }}/{{ slotFor(day, timeSlot)?.capacity }}</span>
                      <span class="text-[10px] opacity-70">available</span>
                    </template>
                  </div>
                </template>
                <template v-else>
                  <span class="text-xs text-sts-muted opacity-40">—</span>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

    </div>
  </main>
</template>
