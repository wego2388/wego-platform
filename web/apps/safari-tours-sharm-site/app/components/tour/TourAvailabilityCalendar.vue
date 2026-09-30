<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { TourSlot } from "@wego/api-contract";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { tourPageCopy } from "../../content/tourPage";
import { addMonths, firstDayOfWeek, monthGrid, monthOf } from "../../utils/availability";

/**
 * Month calendar of bookable days. Only days with at least one open
 * departure can be chosen; everything else is shown but disabled.
 */
const props = defineProps<{ byDay: Map<string, TourSlot[]>; today: string; lastDay: string }>();
const selected = defineModel<string | null>({ default: null });

const locale = useSiteLocale();
const copy = computed(() => tourPageCopy[locale.value].booking);
const intlLocale = computed(() => (locale.value === "ar" ? "ar-EG" : locale.value));

const firstBookable = computed(() => [...props.byDay.keys()].sort()[0] ?? null);
const month = ref(monthOf(selected.value ?? firstBookable.value ?? props.today));
watch(firstBookable, (day) => {
  if (!selected.value && day) month.value = monthOf(day);
});

const minMonth = computed(() => monthOf(props.today));
const maxMonth = computed(() => monthOf(props.lastDay));
const weeks = computed(() => monthGrid(month.value, firstDayOfWeek(locale.value)));

const title = computed(() =>
  new Intl.DateTimeFormat(intlLocale.value, { month: "long", year: "numeric", timeZone: "UTC" }).format(new Date(`${month.value}-01T00:00:00Z`)),
);
const weekdays = computed(() => {
  const start = firstDayOfWeek(locale.value);
  const format = new Intl.DateTimeFormat(intlLocale.value, { weekday: "short", timeZone: "UTC" });
  // 2023-01-01 was a Sunday.
  return Array.from({ length: 7 }, (_, i) => format.format(new Date(Date.UTC(2023, 0, 1 + ((start + i) % 7)))));
});
function dayLabel(day: string) {
  return new Intl.DateTimeFormat(intlLocale.value, { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" }).format(new Date(`${day}T00:00:00Z`));
}
function localDay(day: string) {
  return new Intl.NumberFormat(intlLocale.value).format(Number(day.slice(8)));
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between">
      <button
        type="button"
        class="grid size-9 place-items-center rounded-full hover:bg-sts-sand-soft disabled:opacity-30"
        :disabled="month <= minMonth"
        :aria-label="copy.previousMonth"
        @click="month = addMonths(month, -1)"
      >
        <Icon name="lucide:chevron-left" class="size-5 rtl:-scale-x-100" aria-hidden="true" />
      </button>
      <p class="font-semibold" aria-live="polite">{{ title }}</p>
      <button
        type="button"
        class="grid size-9 place-items-center rounded-full hover:bg-sts-sand-soft disabled:opacity-30"
        :disabled="month >= maxMonth"
        :aria-label="copy.nextMonth"
        @click="month = addMonths(month, 1)"
      >
        <Icon name="lucide:chevron-right" class="size-5 rtl:-scale-x-100" aria-hidden="true" />
      </button>
    </div>
    <table class="mt-2 w-full table-fixed border-separate border-spacing-1 text-center text-sm">
      <thead>
        <tr>
          <th v-for="name in weekdays" :key="name" scope="col" class="pb-1 text-xs font-semibold text-sts-muted">{{ name }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(week, w) in weeks" :key="w">
          <td v-for="(day, d) in week" :key="d" class="p-0">
            <button
              v-if="day"
              type="button"
              class="grid aspect-square w-full place-items-center rounded-full font-semibold tabular-nums transition-colors"
              :class="
                day === selected
                  ? 'bg-sts-ocean text-white'
                  : byDay.has(day)
                    ? 'bg-sts-sand-soft text-sts-ink ring-1 ring-sts-sunset/60 hover:bg-sts-sunset/25'
                    : 'text-sts-muted/60'
              "
              :disabled="!byDay.has(day)"
              :aria-pressed="day === selected"
              :aria-label="byDay.has(day) ? `${dayLabel(day)} — ${copy.available}` : dayLabel(day)"
              @click="selected = day"
            >
              {{ localDay(day) }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
