<script setup lang="ts">
import { computed, ref } from "vue";
import type { OnlineRequestAcknowledgement, SubmitOnlineRequestPayload } from "@wego/api-contract";
import { ALL_NATIONALITIES, isPlausibleEmail, normalizePhone } from "../../content/checkout";
import { onlineRequestCopy } from "../../content/onlineRequest";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { PublicApiError, submitOnlineRequest } from "../../composables/usePublicToursApi";

const props = defineProps<{
  selection: Pick<SubmitOnlineRequestPayload, "tourId" | "preferredDate" | "preferredTime" | "adultsCount" | "childrenCount" | "priceOptionCode" | "unitCount">;
  valid: boolean;
}>();
const emit = defineEmits<{ locked: [value: boolean] }>();
const locale = useSiteLocale();
const localePath = useLocalePath();
const c = computed(() => onlineRequestCopy[locale.value]);
const form = ref({ fullName: "", phone: "", nationality: "", hotelName: "", email: "", specialRequests: "", website: "" });
const consent = ref(false);
const busy = ref(false);
const uncertain = ref(false);
const error = ref<"invalid" | "rejected" | "uncertain" | null>(null);
const saved = ref<OnlineRequestAcknowledgement | null>(null);
const locked = computed(() => busy.value || uncertain.value || !!saved.value);
let attempt: SubmitOnlineRequestPayload | null = null;
const control = "min-h-12 w-full min-w-0 rounded-[var(--sts-radius-control)] border border-sts-border bg-sts-surface px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sts-ocean-bright";
const countries = computed(() => {
  const names = new Intl.DisplayNames([locale.value], { type: "region" });
  return ALL_NATIONALITIES.map(code => ({ code, label: names.of(code) ?? code }))
    .sort((a, b) => a.label.localeCompare(b.label, locale.value));
});

async function submit() {
  if (busy.value || saved.value) return;
  if (!attempt) {
    const f = form.value;
    if (!props.valid || !consent.value || !f.fullName.trim() || !f.hotelName.trim()
      || !ALL_NATIONALITIES.includes(f.nationality) || !/^\+[1-9][0-9]{6,14}$/.test(normalizePhone(f.phone))
      || (f.email.trim() && !isPlausibleEmail(f.email.trim()))) {
      error.value = "invalid"; return;
    }
    attempt = {
      ...props.selection, clientRequestId: crypto.randomUUID(), locale: locale.value,
      customer: { fullName: f.fullName.trim(), phone: normalizePhone(f.phone), nationality: f.nationality, ...(f.email.trim() ? { email: f.email.trim() } : {}) },
      hotelName: f.hotelName.trim(), ...(f.specialRequests.trim() ? { specialRequests: f.specialRequests.trim() } : {}), website: f.website,
    };
  }
  busy.value = true; error.value = null; emit("locked", true);
  try {
    saved.value = await submitOnlineRequest(attempt);
    uncertain.value = false;
  } catch (e) {
    // A 5xx/network/lost response may follow a successful commit. Never change its key/body.
    uncertain.value = uncertain.value || !(e instanceof PublicApiError && e.status >= 400 && e.status < 500);
    error.value = uncertain.value ? "uncertain" : "rejected";
    if (!uncertain.value) attempt = null;
  } finally {
    busy.value = false; emit("locked", locked.value);
  }
}
</script>

<template>
  <section v-if="saved" role="status" class="min-w-0 rounded-2xl border border-sts-ocean-bright bg-sts-sand-soft p-4" data-online-request-success>
    <h3 class="text-lg font-bold">{{ c.success }}</h3>
    <p class="mt-3 text-sm">{{ c.reference }}</p>
    <p class="mt-1 break-all font-mono text-sm font-bold" dir="ltr" data-request-reference>{{ saved.reference }}</p>
    <p class="mt-3 text-sm leading-relaxed">{{ c.next }}</p>
  </section>
  <form v-else class="grid gap-3" data-online-request-form @submit.prevent="submit">
    <p class="rounded-xl bg-sts-sand-soft p-3 text-sm">{{ c.notice }}</p>
    <fieldset :disabled="locked" class="grid min-w-0 gap-3">
      <legend class="sr-only">{{ c.send }}</legend>
      <label class="grid min-w-0 gap-1 text-sm font-semibold">{{ c.name }}<input v-model="form.fullName" name="fullName" autocomplete="name" :class="control" maxlength="200" required></label>
      <label class="grid min-w-0 gap-1 text-sm font-semibold">{{ c.phone }}<input v-model="form.phone" name="phone" type="tel" dir="ltr" autocomplete="tel" :class="control" maxlength="32" required></label>
      <label class="grid min-w-0 gap-1 text-sm font-semibold">{{ c.nationality }}<select v-model="form.nationality" name="nationality" :class="control" required><option value="">{{ c.country }}</option><option v-for="country in countries" :key="country.code" :value="country.code">{{ country.label }}</option></select></label>
      <label class="grid min-w-0 gap-1 text-sm font-semibold">{{ c.hotel }}<input v-model="form.hotelName" name="hotelName" :class="control" maxlength="200" required></label>
      <label class="grid min-w-0 gap-1 text-sm font-semibold">{{ c.email }}<input v-model="form.email" name="email" type="email" autocomplete="email" dir="ltr" :class="control" maxlength="320"></label>
      <label class="grid min-w-0 gap-1 text-sm font-semibold">{{ c.notes }}<textarea v-model="form.specialRequests" name="specialRequests" :class="control" rows="2" maxlength="2000" /></label>
      <label class="hidden" aria-hidden="true">Website<input v-model="form.website" name="website" tabindex="-1" autocomplete="off"></label>
      <label class="flex items-start gap-2 text-xs leading-relaxed"><input v-model="consent" type="checkbox" name="privacy" class="mt-1 size-4 shrink-0" required><span>{{ c.consent }} <NuxtLink :to="localePath('/privacy')" class="underline">{{ c.privacy }}</NuxtLink></span></label>
    </fieldset>
    <p v-if="error" role="alert" class="text-sm text-sts-danger">{{ error === 'uncertain' ? c.error : error === 'invalid' ? c.invalid : c.rejected }}</p>
    <p v-if="!valid && !uncertain" class="text-xs text-sts-muted">{{ c.select }}</p>
    <UiButton type="submit" block size="lg" :disabled="busy || (!valid && !uncertain)" data-trip-enquiry icon="lucide:send">{{ busy ? c.sending : uncertain ? c.retry : c.send }}</UiButton>
  </form>
</template>
