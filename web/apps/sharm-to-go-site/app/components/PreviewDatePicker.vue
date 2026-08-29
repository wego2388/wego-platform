<script setup lang="ts">
import type { SharmLocale } from "../content/locales";
import { formatPreviewMoney, type PreviewDate } from "../content/booking-preview";

defineProps<{
  dates: PreviewDate[];
  fromLabel: string;
  locale: SharmLocale;
  selectedId: string;
  unavailableLabel: string;
}>();

const emit = defineEmits<{ select: [id: string] }>();
</script>

<template>
  <div class="grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">
    <button
      v-for="date in dates"
      :key="date.id"
      type="button"
      class="min-h-28 rounded-2xl border p-3 text-start transition-colors disabled:cursor-not-allowed"
      :class="[
        selectedId === date.id ? 'border-sharm-sea bg-sharm-lagoon shadow-sm' : 'border-sharm-border bg-white',
        !date.available ? 'text-sharm-muted opacity-55' : 'hover:border-sharm-sea-bright',
      ]"
      :disabled="!date.available"
      :aria-pressed="selectedId === date.id"
      @click="emit('select', date.id)"
    >
      <span class="block text-xs font-medium">{{ date.day[locale] }}</span>
      <span class="mt-1 block text-xl font-semibold">{{ date.number }}</span>
      <span v-if="date.available" class="money mt-3 block text-xs font-semibold text-sharm-sea">
        <span class="sr-only">{{ fromLabel }} </span>{{ formatPreviewMoney(locale, 1450 + date.priceDelta) }}
      </span>
      <span v-else class="mt-3 block text-xs">{{ unavailableLabel }}</span>
    </button>
  </div>
</template>
