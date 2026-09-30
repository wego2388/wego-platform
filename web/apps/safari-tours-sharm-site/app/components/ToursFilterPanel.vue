<script setup lang="ts">
import { formatMoney } from "@wego/api-contract";
import { CATEGORY_ORDER } from "../composables/useCatalog";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { useSiteLocale } from "../composables/useSiteLocale";
import { DURATION_BUCKETS, PRICE_CAPS, TIME_SLOTS, useTourFilters } from "../composables/useTourFilters";
import { siteCopy } from "../content/locales";
import { CATEGORY_VISUAL } from "../utils/categoryVisual";

/** Filter controls. State lives in the URL, so the panel can be rendered twice (sidebar and sheet). */
defineProps<{ idPrefix: string }>();
const copy = useDiscoveryCopy();
const locale = useSiteLocale();
const { filters, toggle, update } = useTourFilters();
const chip =
  "inline-flex min-h-10 cursor-pointer items-center gap-2 rounded-full border px-3.5 text-sm font-semibold transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-sts-ocean-bright";
const on = "border-sts-ocean bg-sts-ocean text-white";
const off = "border-sts-border bg-sts-surface text-sts-ink hover:border-sts-ocean-bright";
</script>

<template>
  <div class="grid gap-7">
    <fieldset>
      <legend class="mb-3 text-sm font-bold">{{ copy.tours.category }}</legend>
      <div class="flex flex-wrap gap-2">
        <label v-for="category in CATEGORY_ORDER" :key="category" :class="[chip, filters.categories.includes(category) ? on : off]">
          <input
            type="checkbox"
            class="sr-only"
            :name="`${idPrefix}-cat`"
            :checked="filters.categories.includes(category)"
            @change="toggle('categories', category)"
          >
          <Icon :name="CATEGORY_VISUAL[category].icon" class="size-4" aria-hidden="true" />
          {{ siteCopy[locale].categories[category].name }}
        </label>
      </div>
    </fieldset>

    <fieldset>
      <legend class="mb-3 text-sm font-bold">{{ copy.tours.duration }}</legend>
      <div class="flex flex-wrap gap-2">
        <label v-for="bucket in DURATION_BUCKETS" :key="bucket" :class="[chip, filters.durations.includes(bucket) ? on : off]">
          <input
            type="checkbox"
            class="sr-only"
            :name="`${idPrefix}-dur`"
            :checked="filters.durations.includes(bucket)"
            @change="toggle('durations', bucket)"
          >
          {{ copy.tours.durations[bucket] }}
        </label>
      </div>
    </fieldset>

    <fieldset>
      <legend class="mb-3 text-sm font-bold">{{ copy.tours.timeOfDay }}</legend>
      <div class="flex flex-wrap gap-2">
        <label v-for="slot in TIME_SLOTS" :key="slot" :class="[chip, filters.slots.includes(slot) ? on : off]">
          <input
            type="checkbox"
            class="sr-only"
            :name="`${idPrefix}-time`"
            :checked="filters.slots.includes(slot)"
            @change="toggle('slots', slot)"
          >
          {{ copy.tours.slots[slot] }}
        </label>
      </div>
    </fieldset>

    <fieldset>
      <legend class="mb-3 text-sm font-bold">{{ copy.tours.maxPrice }}</legend>
      <div class="flex flex-wrap gap-2">
        <label :class="[chip, filters.maxPrice === null ? on : off]">
          <input type="radio" class="sr-only" :name="`${idPrefix}-max`" :checked="filters.maxPrice === null" @change="update({ maxPrice: null })">
          {{ copy.tours.any }}
        </label>
        <label v-for="cap in PRICE_CAPS" :key="cap" :class="[chip, filters.maxPrice === cap ? on : off]">
          <input type="radio" class="sr-only" :name="`${idPrefix}-max`" :checked="filters.maxPrice === cap" @change="update({ maxPrice: cap })">
          ≤ {{ formatMoney({ amount: `${cap}.00`, currencyCode: "EUR" }) }}
        </label>
      </div>
    </fieldset>
  </div>
</template>
