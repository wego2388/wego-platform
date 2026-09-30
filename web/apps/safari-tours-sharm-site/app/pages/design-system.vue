<script setup lang="ts">
import { ref } from "vue";
import { CATEGORY_VISUAL, type CategoryKey } from "../utils/categoryVisual";

// Internal showcase of the design system (FRONTEND_MASTER_PLAN_AR.md §6.1).
// Available in development, or when NUXT_PUBLIC_DESIGN_SYSTEM=true; never indexed.
const config = useRuntimeConfig();
if (!import.meta.dev && !config.public.designSystem) {
  throw createError({ statusCode: 404, statusMessage: "Not found", fatal: true });
}
useHead({ title: "Design system · Safari Tours Sharm", meta: [{ name: "robots", content: "noindex, nofollow" }] });

const name = ref("");
const notes = ref("");
const time = ref("");
const agree = ref(false);
const adults = ref(2);
const dialogOpen = ref(false);
const sheetOpen = ref(false);
const tab = ref("overview");
const showErrors = ref(false);

const swatches = [
  "ocean", "ocean-bright", "sunset", "coral", "sand", "sand-soft", "palm",
  "canvas", "surface", "ink", "muted", "border", "success", "warning", "danger",
];
const categories = Object.keys(CATEGORY_VISUAL) as CategoryKey[];
</script>

<template>
  <main id="main-content" class="mx-auto max-w-6xl space-y-14 px-4 py-10 sm:px-8">
    <header class="flex flex-wrap items-center justify-between gap-4">
      <BrandLogo />
      <div class="flex items-center gap-1 rounded-full bg-sts-ocean p-1">
        <SiteLocaleSwitcher />
        <SiteThemeToggle />
      </div>
    </header>

    <section class="space-y-4">
      <h2 class="font-display text-2xl font-bold">Colours</h2>
      <div class="grid grid-cols-3 gap-3 sm:grid-cols-5">
        <div v-for="swatch in swatches" :key="swatch" class="overflow-hidden rounded-[var(--sts-radius-control)] border border-sts-border">
          <div class="h-14" :style="{ background: `var(--sts-color-${swatch})` }" />
          <p class="px-2 py-1 text-xs text-sts-muted">{{ swatch }}</p>
        </div>
      </div>
      <div class="flex flex-wrap gap-2">
        <UiBadge v-for="category in categories" :key="category" :tone="CATEGORY_VISUAL[category].tone" :icon="CATEGORY_VISUAL[category].icon">
          {{ category }}
        </UiBadge>
      </div>
    </section>

    <section class="space-y-3">
      <h2 class="font-display text-2xl font-bold">Typography</h2>
      <p class="font-display text-4xl font-bold">Red Sea sunsets, Sinai stars</p>
      <p lang="ar" dir="rtl" class="text-3xl font-bold" style="font-family: var(--sts-font-arabic-display)">غروب البحر الأحمر ونجوم سيناء</p>
      <p lang="ar" dir="rtl" class="max-w-2xl text-base">نص تجريبي بخط IBM Plex Sans Arabic للفقرات الطويلة، مع أرقام ٣٥ € و 35 € وتباعد أسطر مريح للقراءة.</p>
      <p lang="ru" class="text-base">Закаты Красного моря и звёзды Синая — пример кириллицы.</p>
    </section>

    <BrandSectionDivider />

    <section class="space-y-4">
      <h2 class="font-display text-2xl font-bold">Buttons</h2>
      <div class="flex flex-wrap items-center gap-3">
        <UiButton icon="lucide:calendar-check">Book now</UiButton>
        <UiButton variant="secondary">Details</UiButton>
        <UiButton variant="ghost" icon-end="lucide:arrow-right">See all tours</UiButton>
        <UiButton loading>Paying…</UiButton>
        <UiButton disabled>Sold out</UiButton>
        <UiButton size="sm" to="/tours">Link to tours</UiButton>
      </div>
      <div class="rounded-[var(--sts-radius-card)] bg-sts-ocean p-4"><UiButton variant="inverse" icon="lucide:message-circle">WhatsApp</UiButton></div>
    </section>

    <section class="grid gap-6 md:grid-cols-2">
      <div class="space-y-4">
        <h2 class="font-display text-2xl font-bold">Form</h2>
        <UiErrorSummary
          v-if="showErrors"
          title="Please fix 2 things"
          :errors="[{ fieldId: 'ds-name', message: 'Enter your full name' }, { fieldId: 'ds-time', message: 'Choose a time' }]"
        />
        <UiField id="ds-name" v-slot="{ id, describedBy, invalid }" label="Full name" hint="As on your passport" required :error="showErrors ? 'Enter your full name' : null">
          <UiInput :id="id" v-model="name" :aria-describedby="describedBy" :invalid="invalid" autocomplete="name" />
        </UiField>
        <UiField id="ds-time" v-slot="{ id, invalid }" label="Time" required :error="showErrors ? 'Choose a time' : null">
          <UiSelect :id="id" v-model="time" :invalid="invalid" placeholder="Choose…" :options="[{ value: 'MORNING', label: 'Morning' }, { value: 'SUNSET', label: 'Sunset' }]" />
        </UiField>
        <UiField v-slot="{ id }" label="Special requests">
          <UiTextarea :id="id" v-model="notes" />
        </UiField>
        <UiStepper v-model="adults" label="Adults" hint="12+ years" :min="1" :max="10" />
        <UiCheckbox v-model="agree">I accept the terms and the cancellation policy</UiCheckbox>
        <UiButton variant="secondary" size="sm" @click="showErrors = !showErrors">Toggle errors</UiButton>
      </div>

      <div class="space-y-4">
        <h2 class="font-display text-2xl font-bold">Media mockups</h2>
        <div class="grid grid-cols-2 gap-3">
          <BrandTourMedia v-for="category in categories" :key="category" :category="category" :alt="`${category} tour`" :label="category" />
        </div>
      </div>
    </section>

    <section class="space-y-4">
      <h2 class="font-display text-2xl font-bold">Overlays &amp; disclosure</h2>
      <div class="flex flex-wrap gap-3">
        <UiButton variant="secondary" @click="dialogOpen = true">Open dialog</UiButton>
        <UiButton variant="secondary" @click="sheetOpen = true">Open sheet</UiButton>
        <UiPopover>
          <template #trigger><UiButton variant="secondary">Popover</UiButton></template>
          Free cancellation up to 48 hours before the tour.
        </UiPopover>
        <UiTooltip text="Hotel pickup included"><UiButton variant="ghost" icon="lucide:bus">Tooltip</UiButton></UiTooltip>
      </div>
      <UiDialog v-model:open="dialogOpen" title="Leave the booking?" description="Your selection is kept for 15 minutes.">
        <p class="text-sm">You can come back and finish anytime.</p>
        <template #actions>
          <UiButton variant="secondary" @click="dialogOpen = false">Stay</UiButton>
          <UiButton @click="dialogOpen = false">Leave</UiButton>
        </template>
      </UiDialog>
      <UiSheet v-model:open="sheetOpen" title="Filters" description="Refine the tours">
        <UiStepper v-model="adults" label="Guests" :min="1" :max="10" />
        <template #footer><UiButton block @click="sheetOpen = false">Show 12 tours</UiButton></template>
      </UiSheet>
      <UiTabs v-model="tab" label="Tour sections" :tabs="[{ value: 'overview', label: 'Overview' }, { value: 'included', label: 'Included' }, { value: 'policy', label: 'Policy' }]">
        <template #overview><p class="text-sm">Overview content</p></template>
        <template #included><p class="text-sm">What is included</p></template>
        <template #policy><p class="text-sm">Cancellation policy</p></template>
      </UiTabs>
      <UiAccordion :items="[{ value: 'a', title: 'Is hotel pickup included?', body: 'Yes, from hotels in Sharm El Sheikh.' }, { value: 'b', title: 'What should I bring?', body: 'Sunglasses, sunscreen and comfortable shoes.' }]" />
    </section>

    <section class="grid gap-6 md:grid-cols-2">
      <div class="space-y-3">
        <h2 class="font-display text-2xl font-bold">Loading</h2>
        <UiSkeleton class="h-40" />
        <UiSkeleton class="h-4 w-2/3" />
        <UiSkeleton class="h-4 w-1/2" />
      </div>
      <UiEmptyState title="No tours match these filters" body="Try removing a filter, or ask us on WhatsApp — we'll find the right trip.">
        <UiButton variant="secondary" size="sm">Clear filters</UiButton>
      </UiEmptyState>
    </section>
  </main>
</template>
