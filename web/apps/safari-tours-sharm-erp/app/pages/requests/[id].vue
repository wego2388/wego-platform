<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { moneyToMinorUnits, type OnlineRequest, type Tour, type TourSlot } from "@wego/api-contract";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../../composables/useAuthSession";
import { actOnOnlineRequest, getOnlineRequest, getOnlineRequestHistory } from "../../composables/useOnlineRequests";
import { listSlotsByDate, ToursApiError } from "../../composables/useToursApi";
import { useErpLocale } from "../../composables/useErpLocale";
import { requestMessages } from "../../utils/onlineRequestMessages";
import { estimateTotal } from "../../utils/officeBooking";
import { isCalendarDate, operatorCalendarDay } from "../../utils/calendarDate";
import CalendarDateField from "../../components/CalendarDateField.vue";
import DepartureCreator from "../../components/DepartureCreator.vue";

const route = useRoute();
const { locale, t, dateLabel, instantLabel, money } = useErpLocale();
const c = computed(() => requestMessages[locale.value]);
useHead(() => ({ title: `${c.value.title} · Safari Tours Sharm` }));
const id = String(route.params.id);
const session = ref<AuthSession | null>(null);
const item = ref<OnlineRequest | null>(null); const tour = ref<Tour | null>(null);
const history = ref<Awaited<ReturnType<typeof getOnlineRequestHistory>>>([]);
const slots = ref<TourSlot[]>([]); const date = ref(""); const slotId = ref("");
const loading = ref(false); const busy = ref(false); const slotsLoading = ref(false);
const failed = ref(false); const actionError = ref(""); const agreed = ref(false); const closeAgreed = ref(false);
const uncertain = ref(false);
let attempt: { action: "follow-up" | "convert"; payload: unknown } | null = null;
let slotSequence = 0;
const canManage = computed(() => hasPermission(session.value, "tours-operator.booking:create-office"));
const canManageSlots = computed(() => hasPermission(session.value, "tours-operator.slot:manage"));
const open = computed(() => item.value?.status === "NEW" || item.value?.status === "IN_PROGRESS");
const locked = computed(() => busy.value || uncertain.value);
const estimate = computed(() => item.value && tour.value ? estimateTotal(tour.value, {
  adults: item.value.adultsCount, children: item.value.childrenCount,
  optionCode: item.value.priceOptionCode ?? "", unitCount: item.value.unitCount ?? 1,
}) : null);
const selectedSlot = computed(() => slots.value.find(s => s.id === slotId.value));
const priceDiffers = computed(() => estimate.value?.ok && item.value && estimate.value.total.amount !== item.value.estimatedTotal.amount);
const canConvert = computed(() => canManage.value && open.value && tour.value?.isActive && agreed.value && estimate.value?.ok && selectedSlot.value
  && !selectedSlot.value.isBlocked && selectedSlot.value.available >= estimate.value.seats && !slotsLoading.value);

function authFailure(e: unknown) {
  if (e instanceof ToursApiError && e.status === 401) { clearAuthSession(); void navigateTo("/login"); }
}
async function load() {
  if (!session.value || locked.value) return;
  loading.value = true; failed.value = false; agreed.value = false; actionError.value = "";
  try {
    const request = await getOnlineRequest(session.value.token, id);
    item.value = request;
    tour.value = request.tour;
    history.value = await getOnlineRequestHistory(session.value.token, id);
    date.value = request.preferredDate < operatorCalendarDay() ? operatorCalendarDay() : request.preferredDate;
    await loadSlots();
  } catch (e) { authFailure(e); failed.value = true; }
  finally { loading.value = false; }
}
async function loadSlots() {
  const seq = ++slotSequence; slots.value = []; slotId.value = ""; agreed.value = false;
  if (!session.value || !item.value || !isCalendarDate(date.value) || date.value < operatorCalendarDay()) return;
  slotsLoading.value = true;
  try {
    const result = await listSlotsByDate(session.value.token, item.value.tourId, date.value);
    if (seq !== slotSequence) return;
    slots.value = result;
  } catch (e) { authFailure(e); if (seq === slotSequence) actionError.value = c.value.failed; }
  finally { if (seq === slotSequence) slotsLoading.value = false; }
}
watch(date, () => { void loadSlots(); });
watch(slotId, () => { agreed.value = false; });
async function savedDeparture() { await loadSlots(); }
async function expired() { clearAuthSession(); await navigateTo("/login"); }
async function act(action: "follow-up" | "convert", payload: unknown) {
  if (!session.value || !item.value || busy.value) return;
  if (!attempt) attempt = { action, payload };
  busy.value = true; actionError.value = "";
  try {
    item.value = await actOnOnlineRequest(session.value.token, id, attempt.action, attempt.payload);
    uncertain.value = false; attempt = null; agreed.value = false; closeAgreed.value = false;
    // A history read failure must not turn a successful mutation into an unsafe retry.
    history.value = await getOnlineRequestHistory(session.value.token, id).catch(() => history.value);
  } catch (e) {
    authFailure(e);
    uncertain.value = uncertain.value || !(e instanceof ToursApiError && e.status >= 400 && e.status < 500);
    actionError.value = uncertain.value ? c.value.uncertain : e instanceof ToursApiError && e.errorCode === "price_changed" ? c.value.price_changed : c.value.conflict;
    if (!uncertain.value) attempt = null;
  } finally { busy.value = false; }
}
function follow(status: "IN_PROGRESS" | "CLOSED") {
  if (!canManage.value || !item.value || !open.value || locked.value || (status === "CLOSED" && !closeAgreed.value)) return;
  void act("follow-up", { expectedRevision: item.value.revision, status });
}
function convert() {
  if (!canConvert.value || !item.value || !estimate.value?.ok || locked.value) return;
  void act("convert", { expectedRevision: item.value.revision, slotId: slotId.value, expectedTotalCents: Number(moneyToMinorUnits(estimate.value.total)) });
}
function retry() { if (attempt) void act(attempt.action, attempt.payload); }
onMounted(async () => {
  session.value = readAuthSession();
  if (!session.value) { await navigateTo("/login"); return; }
  if (!hasPermission(session.value, "tours-operator.booking:view")) { await navigateTo("/"); return; }
  await load();
});
</script>

<template>
  <main class="mx-auto max-w-5xl space-y-5 px-4 py-8 sm:px-8 lg:px-12">
    <NuxtLink to="/requests" class="text-sm font-semibold text-sts-ocean">← {{ c.back }}</NuxtLink>
    <header class="flex flex-wrap items-center justify-between gap-3"><h1 class="text-2xl font-bold">{{ c.title }}</h1><button class="min-h-11 rounded-xl border border-sts-border px-4" :disabled="loading || locked" @click="load">{{ c.refresh }}</button></header>
    <p v-if="failed" role="alert" class="text-sts-danger">{{ c.failed }}</p><p v-if="loading" role="status">{{ c.loading }}</p>
    <template v-if="item">
      <article class="min-w-0 rounded-2xl border border-sts-border bg-sts-surface p-5">
        <div class="flex flex-wrap items-center justify-between gap-3"><h2 class="text-lg font-bold">{{ tour?.nameEn ?? tour?.slug }}</h2><span class="rounded-lg bg-sts-sand-soft px-3 py-1 text-sm font-semibold">{{ c[item.status] }}</span></div>
        <p class="mt-2 break-all font-mono text-xs" dir="ltr">{{ item.reference }}</p><p class="mt-2 text-xs text-sts-muted">{{ c.source }}</p>
        <dl class="mt-5 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-3 text-sm">
          <dt class="text-sts-muted">{{ c.name }}</dt><dd class="break-words">{{ item.customer.fullName }}</dd>
          <dt class="text-sts-muted">{{ c.phone }}</dt><dd class="break-words"><span dir="ltr">{{ item.customer.phone }}</span></dd>
          <dt class="text-sts-muted">{{ c.nationality }}</dt><dd>{{ item.customer.nationality }}</dd>
          <dt class="text-sts-muted">{{ c.hotel }}</dt><dd class="break-words">{{ item.hotelName }}</dd>
          <dt v-if="item.customer.email" class="text-sts-muted">{{ c.email }}</dt><dd v-if="item.customer.email" class="break-all">{{ item.customer.email }}</dd>
          <dt class="text-sts-muted">{{ c.preferred }}</dt><dd>{{ dateLabel(item.preferredDate) }} · {{ item.preferredTime ? t(`slot.${item.preferredTime}`) : '—' }}</dd>
          <dt class="text-sts-muted">{{ c.guests }}</dt><dd>{{ item.adultsCount }} + {{ item.childrenCount }}<span v-if="item.unitCount"> · {{ item.unitCount }} × {{ item.priceOptionCode }}</span></dd>
          <dt class="text-sts-muted">{{ c.locale }}</dt><dd>{{ item.locale.toUpperCase() }}</dd>
          <dt class="text-sts-muted">{{ c.estimate }}</dt><dd class="font-bold">{{ money(item.estimatedTotal) }}</dd>
          <dt v-if="item.specialRequests" class="text-sts-muted">{{ c.notes }}</dt><dd v-if="item.specialRequests" class="whitespace-pre-wrap break-words">{{ item.specialRequests }}</dd>
        </dl>
      </article>
      <p v-if="!canManage" class="text-sm text-sts-muted">{{ c.readonly }}</p>
      <div v-if="item.bookingId" class="rounded-2xl border border-sts-border bg-sts-surface p-5"><NuxtLink :to="`/bookings/${item.bookingId}`" class="inline-block rounded-xl bg-sts-ocean px-5 py-3 font-semibold text-white">{{ c.booking }}</NuxtLink></div>
      <template v-if="open && canManage">
        <fieldset :disabled="locked || loading" class="grid min-w-0 gap-4 rounded-2xl border border-sts-border bg-sts-surface p-5">
          <legend class="px-2 font-bold">{{ c.convert }}</legend>
          <CalendarDateField id="request-departure-day" v-model="date" :label="c.date" :min="operatorCalendarDay()" :disabled="locked" required />
          <label class="grid gap-2 text-sm font-semibold">{{ c.slot }}<select v-model="slotId" class="min-h-12 w-full min-w-0 rounded-xl border border-sts-border bg-sts-surface px-3" :disabled="slotsLoading"><option value="">{{ c.slot }}</option><option v-for="s in slots" :key="s.id" :value="s.id" :disabled="s.isBlocked || (estimate?.ok ? s.available < estimate.seats : true)">{{ dateLabel(s.date) }} · {{ t(`slot.${s.timeSlot}`) }} · {{ s.available }}</option></select></label>
          <p v-if="!slotsLoading && !slots.some(s => !s.isBlocked && s.available > 0)" class="text-sm text-sts-muted">{{ c.noSlots }}</p>
          <p v-if="estimate?.ok" class="text-sm">{{ c.current }}: <strong class="text-lg">{{ money(estimate.total) }}</strong></p>
          <p v-if="priceDiffers" role="status" class="rounded-xl bg-sts-sand-soft p-3 text-sm">{{ c.changed }}</p>
          <label class="flex items-start gap-2 text-sm leading-relaxed"><input v-model="agreed" type="checkbox" class="mt-1 size-4 shrink-0">{{ c.agree }}</label>
          <button type="button" class="min-h-12 rounded-xl bg-sts-ocean px-5 py-3 font-semibold text-white disabled:opacity-40" :disabled="!canConvert || locked" @click="convert">{{ busy ? c.pending : c.convert }}</button>
        </fieldset>
        <DepartureCreator v-if="session && tour && canManageSlots && isCalendarDate(date) && date >= operatorCalendarDay()" :key="tour.id" :token="session.token" :tour="tour" :date="date" :disabled="locked" @saved="savedDeparture" @conflict="loadSlots" @expired="expired" />
        <fieldset :disabled="locked || loading" class="grid gap-4 rounded-2xl border border-sts-border bg-sts-surface p-5">
          <legend class="px-2 font-bold">{{ c.history }}</legend>
          <button v-if="item.status === 'NEW'" type="button" class="min-h-11 rounded-xl border border-sts-border px-4 font-semibold" @click="follow('IN_PROGRESS')">{{ c.start }}</button>
          <label class="flex items-start gap-2 text-sm"><input v-model="closeAgreed" type="checkbox" class="mt-1 size-4 shrink-0">{{ c.closeCheck }}</label>
          <button type="button" class="min-h-11 rounded-xl border border-sts-border px-4 font-semibold disabled:opacity-40" :disabled="!closeAgreed" @click="follow('CLOSED')">{{ c.close }}</button>
        </fieldset>
      </template>
      <p v-if="actionError" role="alert" class="text-sts-danger">{{ actionError }}</p>
      <button v-if="uncertain" type="button" class="min-h-12 rounded-xl bg-sts-ocean px-5 py-3 font-semibold text-white" :disabled="busy" @click="retry">{{ c.retry }}</button>
      <section class="rounded-2xl border border-sts-border bg-sts-surface p-5"><h2 class="font-bold">{{ c.history }}</h2><ol class="mt-3 space-y-2 text-sm"><li v-for="(event, index) in history" :key="index">{{ c[event.status] }} · {{ instantLabel(event.occurredAt) }}<span v-if="event.actorUserId" class="block break-all font-mono text-xs text-sts-muted">{{ event.actorUserId }}</span></li></ol></section>
    </template>
  </main>
</template>
