<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { WegoAlert, WegoButton, WegoInput, WegoSelect, WegoTextarea } from "@wego/ui";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../../composables/useAuthSession";
import {
  ToursApiError, createOfficeBooking, listSlotsByDate, listTours,
  type Booking, type Tour, type TourSlot,
} from "../../composables/useToursApi";
import { useErpLocale } from "../../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../../utils/bookingMessages";
import { estimateTotal, officeErrorMessage, totalsDiffer, validateOfficeForm } from "../../utils/officeBooking";

const { t, direction, count, money, dateLabel } = useErpLocale();
useHead(() => ({ title: `${t("office.new.title")} · Safari Tours Sharm` }));

const route = useRoute();
const router = useRouter();
const session = ref<AuthSession | null>(null);
const canCreate = computed(() => hasPermission(session.value, "tours-operator.booking:create-office"));

const tours = ref<Tour[]>([]);
const slots = ref<TourSlot[]>([]);
const loadState = ref<"loading" | "loaded" | "error">("loading");
const slotsState = ref<"idle" | "loading" | "loaded" | "error">("idle");

const operatorToday = new Date().toLocaleDateString("sv-SE", { timeZone: "Africa/Cairo" });
const tourId = ref("");
const date = ref(operatorToday);
const slotId = ref("");
const adults = ref("2");
const children = ref("0");
const optionCode = ref("");
const unitCount = ref("1");
const fullName = ref("");
const phone = ref("");
const nationality = ref("EG");
const email = ref("");
const hotelName = ref("");
const hotelRoom = ref("");
const specialRequests = ref("");
const customerLocale = ref<"en" | "ar" | "ru" | "it">("en");

const submitted = ref(false);
const submitState = ref<"idle" | "submitting" | "error">("idle");
const submitError = ref<ErpMessageDescriptor | null>(null);
const created = ref<Booking | null>(null);
/** What the form showed while typing, kept to compare with the server's saved total. */
const shownEstimate = ref<ReturnType<typeof estimateTotal> | null>(null);
const createdDiffers = computed(() => !!created.value && totalsDiffer(created.value.totalPrice, shownEstimate.value));
/**
 * One key per form attempt: a retry after a lost response re-sends the same key, so the
 * server returns the booking it already created instead of taking the places twice.
 */
let clientRequestId: string = crypto.randomUUID();

const tour = computed(() => tours.value.find((x) => x.id === tourId.value) ?? null);
const slot = computed(() => slots.value.find((x) => x.id === slotId.value) ?? null);
const perUnit = computed(() => tour.value?.priceBasis === "PER_UNIT");
const estimate = computed(() => tour.value
  ? estimateTotal(tour.value, {
    adults: Number(adults.value), children: Number(children.value),
    optionCode: optionCode.value, unitCount: Number(unitCount.value),
  })
  : null);
const seatsNeeded = computed(() => estimate.value?.ok ? estimate.value.seats : null);
const fitsSlot = computed(() => !slot.value || seatsNeeded.value === null || seatsNeeded.value <= slot.value.available);
const fieldErrors = computed(() => validateOfficeForm({
  tourId: tourId.value, slotId: slotId.value, fullName: fullName.value, phone: phone.value,
  nationality: nationality.value, email: email.value, hotelName: hotelName.value,
}));
const errorText = (key?: string) => (submitted.value && key ? t(key as never) : undefined);
const messageText = (m: ErpMessageDescriptor | null) => (m ? t(m.key, m.params) : "");

function tourLabel(x: Tour): string { return x.nameEn ?? x.slug; }
function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) { clearAuthSession(); void router.replace("/login"); }
}

let slotSeq = 0;
let preferredSlotId = "";
async function loadSlots() {
  slots.value = [];
  slotsState.value = "idle";
  if (!session.value || !tourId.value || !/^\d{4}-\d{2}-\d{2}$/.test(date.value)) return;
  const seq = ++slotSeq;
  slotsState.value = "loading";
  try {
    const result = await listSlotsByDate(session.value.token, tourId.value, date.value);
    if (seq !== slotSeq) return;
    slots.value = result.filter((s) => !s.isBlocked);
    slotsState.value = "loaded";
    // A link from the slot calendar pre-selects its departure once; otherwise a lone slot is chosen for the user.
    const preferred = slots.value.find((s) => s.id === preferredSlotId);
    preferredSlotId = "";
    slotId.value = preferred ? preferred.id : slots.value.length === 1 ? slots.value[0]!.id : "";
  } catch (err) {
    if (seq !== slotSeq) return;
    handleApiError(err);
    slotsState.value = "error";
  }
}

watch([tourId, date], () => {
  slotId.value = "";
  optionCode.value = tour.value?.priceBasis === "PER_UNIT" ? (tour.value.priceOptions[0]?.code ?? "") : "";
  void loadSlots();
});

async function submit() {
  submitted.value = true;
  if (!session.value || Object.keys(fieldErrors.value).length || !tour.value || !estimate.value?.ok) return;
  if (!fitsSlot.value) return;
  submitState.value = "submitting";
  submitError.value = null;
  shownEstimate.value = estimate.value;
  try {
    created.value = await createOfficeBooking(session.value.token, {
      clientRequestId,
      slotId: slotId.value,
      adultsCount: Number(adults.value),
      childrenCount: Number(children.value),
      ...(perUnit.value ? { priceOptionCode: optionCode.value, unitCount: Number(unitCount.value) } : {}),
      customer: {
        fullName: fullName.value.trim(), phone: phone.value.trim(),
        nationality: nationality.value.trim().toUpperCase(),
        ...(email.value.trim() ? { email: email.value.trim() } : {}),
      },
      hotelName: hotelName.value.trim(),
      ...(hotelRoom.value.trim() ? { hotelRoom: hotelRoom.value.trim() } : {}),
      ...(specialRequests.value.trim() ? { specialRequests: specialRequests.value.trim() } : {}),
      locale: customerLocale.value,
    });
    submitState.value = "idle";
  } catch (err) {
    handleApiError(err);
    submitState.value = "error";
    submitError.value = officeErrorMessage(err);
    // A definite refusal (not a lost connection) ends this attempt; the next try is a new request.
    if (err instanceof ToursApiError) clientRequestId = crypto.randomUUID();
    if (err instanceof ToursApiError && err.errorCode === "slot_fully_booked") void loadSlots();
  }
}

function startAnother() {
  created.value = null;
  submitted.value = false;
  submitState.value = "idle";
  fullName.value = ""; phone.value = ""; email.value = ""; hotelName.value = ""; hotelRoom.value = ""; specialRequests.value = "";
  clientRequestId = crypto.randomUUID();
  void loadSlots();
}

onMounted(async () => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  try {
    tours.value = (await listTours(session.value.token, { activeOnly: true, size: 100 })).filter((x) => x.isActive);
    loadState.value = "loaded";
  } catch (err) {
    handleApiError(err);
    loadState.value = "error";
    return;
  }
  const q = route.query;
  if (typeof q.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(q.date)) date.value = q.date;
  if (typeof q.slotId === "string") preferredSlotId = q.slotId;
  if (typeof q.tourId === "string" && tours.value.some((x) => x.id === q.tourId)) tourId.value = q.tourId;
});
</script>

<template>
  <main class="px-4 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">
      <header>
        <NuxtLink to="/bookings" class="text-sm text-sts-muted hover:text-sts-ocean"><span aria-hidden="true">{{ direction === 'rtl' ? '→' : '←' }}</span> {{ t('nav.bookings') }}</NuxtLink>
        <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ t('office.new.title') }}</h1>
        <p class="mt-1 text-sm text-sts-muted">{{ t('office.new.intro') }}</p>
      </header>

      <WegoAlert v-if="session && !canCreate" variant="danger" class="mt-6">{{ t('office.new.noPermission') }}</WegoAlert>
      <WegoAlert v-else-if="loadState === 'error'" variant="danger" class="mt-6">{{ t('office.new.loadFailed') }}</WegoAlert>

      <section v-else-if="created" class="mt-6 rounded-2xl border border-sts-border bg-sts-surface p-6" role="status">
        <WegoAlert variant="success">{{ t('office.new.created', { reference: created.reference }) }}</WegoAlert>
        <p class="mt-3 text-sm font-semibold tabular-nums">{{ t('office.new.serverTotal', { total: money(created.totalPrice) }) }}</p>
        <p v-if="created.officePayment" class="mt-1 text-sm">{{ t('office.new.paymentState', { state: t(`office.badge.${created.officePayment.state}`) }) }}</p>
        <WegoAlert v-if="createdDiffers && shownEstimate?.ok" variant="warning" class="mt-3" role="alert">{{ t('office.new.totalDiffers', { server: money(created.totalPrice), estimate: money(shownEstimate.total) }) }}</WegoAlert>
        <p class="mt-3 text-sm">{{ t('office.new.createdNext') }}</p>
        <div class="mt-4 flex flex-wrap gap-3">
          <NuxtLink :to="`/bookings/${created.id}`" class="rounded-xl bg-sts-ocean px-4 py-2 text-sm font-semibold text-white">{{ t('office.new.openBooking') }}</NuxtLink>
          <button type="button" class="rounded-xl border border-sts-border px-4 py-2 text-sm font-semibold" @click="startAnother">{{ t('office.new.another') }}</button>
        </div>
      </section>

      <form v-else-if="loadState === 'loaded'" class="mt-6 space-y-6" novalidate @submit.prevent="submit">
        <WegoAlert v-if="submitState === 'error'" variant="danger" role="alert">{{ messageText(submitError) }}</WegoAlert>

        <fieldset class="rounded-2xl border border-sts-border bg-sts-surface p-5">
          <legend class="px-1 text-sm font-semibold text-sts-muted">{{ t('office.new.tripSection') }}</legend>
          <div class="grid gap-4 sm:grid-cols-2">
            <WegoSelect id="office-tour" v-model="tourId" :label="t('common.tour')" required :error="errorText(fieldErrors.tourId)">
              <option value="">{{ t('office.new.chooseTour') }}</option>
              <option v-for="x in tours" :key="x.id" :value="x.id">{{ tourLabel(x) }}</option>
            </WegoSelect>
            <WegoInput id="office-date" v-model="date" type="date" :min="operatorToday" :label="t('common.date')" required />
            <WegoSelect id="office-slot" v-model="slotId" :label="t('office.new.slot')" required :disabled="!slots.length" :error="errorText(fieldErrors.slotId)" :help="slotsState === 'loaded' && !slots.length ? t('office.new.noSlots') : undefined">
              <option value="">{{ t('office.new.chooseSlot') }}</option>
              <option v-for="s in slots" :key="s.id" :value="s.id" :disabled="s.available === 0">
                {{ t(`slot.${s.timeSlot}`) }} — {{ t('office.new.placesLeft', { available: count(s.available), capacity: count(s.capacity) }) }}
              </option>
            </WegoSelect>
            <p v-if="slot" class="self-end text-sm" :class="fitsSlot ? 'text-sts-muted' : 'font-semibold text-sts-danger'" aria-live="polite">
              {{ fitsSlot ? t('office.new.placesLeftLive', { available: count(slot.available) }) : t('office.new.notEnoughPlaces', { available: count(slot.available), needed: count(seatsNeeded ?? 0) }) }}
            </p>
          </div>
        </fieldset>

        <fieldset v-if="tour" class="rounded-2xl border border-sts-border bg-sts-surface p-5">
          <legend class="px-1 text-sm font-semibold text-sts-muted">{{ t('office.new.partySection') }}</legend>
          <div class="grid gap-4 sm:grid-cols-2">
            <WegoInput id="office-adults" v-model="adults" type="number" min="1" max="50" :label="t('booking.adults')" required />
            <WegoInput id="office-children" v-model="children" type="number" min="0" max="50" :label="t('booking.children')" :help="!perUnit && !tour.priceChild ? t('office.new.noChildPrice') : undefined" />
            <template v-if="perUnit">
              <WegoSelect id="office-option" v-model="optionCode" :label="t('office.new.option')" required>
                <option v-for="o in tour.priceOptions" :key="o.code" :value="o.code">{{ o.label }} — {{ money(o.price) }} · {{ t('office.new.seatsPerUnit', { seats: count(o.seatsPerUnit) }) }}</option>
              </WegoSelect>
              <WegoInput id="office-units" v-model="unitCount" type="number" min="1" max="50" :label="t('office.new.units')" required />
            </template>
          </div>
          <p class="mt-4 text-sm" aria-live="polite">
            <template v-if="estimate?.ok">
              <span class="text-sts-muted">{{ t('office.new.price') }}</span>
              <strong class="money ms-2 text-lg tabular-nums">{{ money(estimate.total) }}</strong>
              <span class="ms-2 text-xs text-sts-muted">{{ t('office.new.priceNote') }}</span>
            </template>
            <span v-else-if="estimate" class="font-semibold text-sts-danger">{{ t(estimate.key) }}</span>
          </p>
        </fieldset>

        <fieldset class="rounded-2xl border border-sts-border bg-sts-surface p-5">
          <legend class="px-1 text-sm font-semibold text-sts-muted">{{ t('common.customer') }}</legend>
          <div class="grid gap-4 sm:grid-cols-2">
            <WegoInput id="office-name" v-model="fullName" :label="t('booking.name')" autocomplete="off" required :error="errorText(fieldErrors.fullName)" />
            <WegoInput id="office-phone" v-model="phone" type="tel" dir="ltr" :label="t('booking.phone')" autocomplete="off" required :error="errorText(fieldErrors.phone)" />
            <WegoInput id="office-nationality" v-model="nationality" maxlength="2" dir="ltr" :label="t('office.new.nationality')" :help="t('office.new.nationalityHelp')" required :error="errorText(fieldErrors.nationality)" />
            <WegoInput id="office-email" v-model="email" type="email" dir="ltr" :label="t('office.new.emailOptional')" autocomplete="off" :error="errorText(fieldErrors.email)" />
            <WegoInput id="office-hotel" v-model="hotelName" :label="t('common.hotel')" required :error="errorText(fieldErrors.hotelName)" />
            <WegoInput id="office-room" v-model="hotelRoom" maxlength="32" :label="t('office.new.roomOptional')" />
            <WegoSelect id="office-locale" v-model="customerLocale" :label="t('office.new.customerLanguage')">
              <option value="en">English</option>
              <option value="ar">العربية</option>
              <option value="ru">Русский</option>
              <option value="it">Italiano</option>
            </WegoSelect>
          </div>
          <WegoTextarea id="office-notes" v-model="specialRequests" class="mt-4" rows="3" maxlength="4000" :label="t('office.new.notes')" />
        </fieldset>

        <div class="flex flex-wrap items-center gap-4">
          <WegoButton type="submit" variant="primary" :disabled="submitState === 'submitting'">
            {{ submitState === 'submitting' ? t('office.new.submitting') : t('office.new.submit') }}
          </WegoButton>
          <p class="text-sm text-sts-muted">{{ t('office.new.unpaidNote') }}</p>
        </div>
        <p v-if="date" class="sr-only">{{ dateLabel(date) }}</p>
      </form>
      <p v-else class="mt-6 text-sm text-sts-muted" role="status">{{ t('common.loading') }}</p>
    </div>
  </main>
</template>
