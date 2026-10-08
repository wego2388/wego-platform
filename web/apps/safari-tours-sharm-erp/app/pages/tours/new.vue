<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { WegoAlert, WegoButton, WegoInput, WegoSelect, WegoTextarea } from "@wego/ui";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../../composables/useAuthSession";
import { createTour, ToursApiError, type Tour } from "../../composables/useToursApi";
import { useErpLocale } from "../../composables/useErpLocale";
import { ENTRY_CATEGORIES, ENTRY_TIMES, tourEntryPayload, type TourEntry } from "../../utils/tourEntry";
import { tourErrorMessage } from "../../utils/tourMessages";
import type { ErpMessageDescriptor } from "../../utils/bookingMessages";
const { t } = useErpLocale();
useHead(() => ({ title: `${t('inventory.newTour')} · Safari Tours Sharm` }));
const router = useRouter(); const session = ref<AuthSession | null>(null);
const canManage = computed(() => hasPermission(session.value, "tours-operator.tour:manage"));
const form = reactive<TourEntry>({ name: "", slug: "", category: "DESERT", duration: "", adult: "", child: "", capacity: "", times: [], order: "0", type: "TOUR", pricingNote: "", confirmed: false });
const busy = ref(false); const error = ref<ErpMessageDescriptor | null>(null); const created = ref<Tour | null>(null);
watch(() => [form.name, form.slug, form.category, form.duration, form.adult, form.child, form.capacity, form.times.join(), form.order, form.type, form.pricingNote], () => { form.confirmed = false; });
async function save() {
  if (!session.value || !canManage.value || busy.value || created.value) return;
  error.value = null;
  const payload = tourEntryPayload(form);
  if (!payload) { error.value = { key: "inventory.invalidTour" }; return; }
  busy.value = true;
  try { created.value = await createTour(session.value.token, payload); }
  catch (e) {
    if (e instanceof ToursApiError && e.status === 401) { clearAuthSession(); void router.replace("/login"); }
    error.value = e instanceof ToursApiError && e.errorCode === "slug_already_exists" ? { key: "inventory.slugTaken" } : tourErrorMessage(e);
  } finally { busy.value = false; }
}
onMounted(() => { session.value = readAuthSession(); if (!session.value) void router.replace("/login"); });
</script>

<template>
  <main class="px-4 py-8 text-sts-ink sm:px-10">
    <div class="mx-auto max-w-3xl">
      <NuxtLink to="/tours" class="text-sm font-semibold text-sts-ocean hover:underline">{{ t('nav.tours') }}</NuxtLink>
      <h1 class="mt-2 text-2xl font-semibold">{{ t('inventory.newTour') }}</h1>
      <p class="mt-2 text-sm text-sts-muted">{{ t('inventory.tourIntro') }}</p>
      <WegoAlert v-if="session && !canManage" variant="danger" class="mt-6">{{ t('common.forbidden') }}</WegoAlert>
      <section v-else-if="created" class="mt-6 space-y-4 rounded-2xl border border-sts-border bg-sts-surface p-5">
        <WegoAlert variant="success" role="status">{{ t('inventory.tourSaved') }}</WegoAlert>
        <p dir="auto" class="font-semibold">{{ created.nameEn }} · {{ created.slug }}</p>
        <NuxtLink :to="`/tours/${created.id}/content`" class="inline-block rounded-xl bg-sts-ocean px-4 py-2 text-sm font-semibold text-white">{{ t('inventory.openContent') }}</NuxtLink>
      </section>
      <form v-else-if="canManage" class="mt-6 space-y-4 rounded-2xl border border-sts-border bg-sts-surface p-5" novalidate @submit.prevent="save">
        <WegoAlert v-if="error" variant="danger" role="alert">{{ t(error.key, error.params) }}</WegoAlert>
        <fieldset :disabled="busy" class="space-y-4">
          <div class="grid gap-4 sm:grid-cols-2">
            <WegoInput id="tour-name" v-model="form.name" :label="t('inventory.name')" maxlength="200" dir="ltr" required />
            <WegoInput id="tour-slug" v-model="form.slug" :label="t('inventory.slug')" :help="t('inventory.slugHint')" maxlength="80" dir="ltr" required />
            <WegoSelect id="tour-category" v-model="form.category" :label="t('inventory.category')" required><option v-for="category in ENTRY_CATEGORIES" :key="category" :value="category">{{ t(`category.${category}`) }}</option></WegoSelect>
            <WegoSelect id="tour-type" v-model="form.type" :label="t('inventory.type')" required><option value="TOUR">{{ t('inventory.typeTour') }}</option><option value="TRANSFER">{{ t('inventory.typeTransfer') }}</option><option value="REQUEST_ONLY">{{ t('inventory.typeRequest') }}</option></WegoSelect>
            <WegoInput id="tour-duration" v-model="form.duration" :label="t('inventory.duration')" maxlength="80" required />
            <WegoInput id="tour-capacity" v-model="form.capacity" :label="t('inventory.capacity')" type="number" min="1" max="1000" required />
            <WegoInput id="tour-adult" v-model="form.adult" :label="t('inventory.adult')" inputmode="decimal" dir="ltr" required />
            <WegoInput id="tour-child" v-model="form.child" :label="t('inventory.child')" inputmode="decimal" dir="ltr" />
            <WegoInput id="tour-order" v-model="form.order" :label="t('inventory.order')" type="number" min="0" required />
          </div>
          <p class="text-sm text-sts-muted">{{ t('inventory.priceHint') }}</p>
          <fieldset><legend class="mb-2 text-sm font-semibold">{{ t('inventory.times') }}</legend><div class="flex flex-wrap gap-4"><label v-for="time in ENTRY_TIMES" :key="time" class="flex items-center gap-2 text-sm"><input v-model="form.times" type="checkbox" :value="time">{{ t(`slot.${time}`) }}</label></div></fieldset>
          <WegoTextarea id="tour-pricing-note" v-model="form.pricingNote" :label="t('inventory.pricingNote')" maxlength="500" rows="2" />
          <p class="text-sm text-sts-muted">{{ t('inventory.policy') }}</p>
          <label class="flex items-start gap-2 text-sm"><input id="tour-confirm" v-model="form.confirmed" type="checkbox" class="mt-1" required> {{ t('inventory.factsConfirm') }}</label>
          <WegoButton type="submit" :disabled="busy">{{ t(busy ? 'inventory.saving' : 'inventory.saveTour') }}</WegoButton>
        </fieldset>
      </form>
    </div>
  </main>
</template>
