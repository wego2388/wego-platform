<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { getSalesControl, updateSalesControl, type SalesControl } from "../composables/useSalesControlApi";
import { TravelMarketplaceApiError } from "../composables/useTravelMarketplaceApi";

definePageMeta({ layout: "app-shell" });
useHead({ title: "Request availability · Sharm To Go" });
const locale = ref<"ar" | "en">("ar");
const text = computed(() => ({
  ar: { title: "استقبال الطلبات", description: "تحكم في استقبال طلبات جديدة، مع استمرار متابعة الطلبات الحالية.", noAccess: "تحتاج صلاحية إدارة استقبال الطلبات.", paused: "استقبال الطلبات الجديدة متوقف", open: "استقبال الطلبات الجديدة متاح", loading: "جاري تحميل الحالة…", note: "سبب داخلي اختياري — لا يظهر للعميل", pause: "إيقاف استقبال طلبات جديدة", resume: "استئناف استقبال الطلبات", saving: "جاري الحفظ…", refresh: "تحديث الحالة", error: "تعذر حفظ أو تحميل الحالة. حاول مرة أخرى.", conflict: "غيّر مدير آخر الحالة. راجع الحالة المحدثة قبل المحاولة مرة أخرى.", expired: "انتهت جلسة الدخول. سجّل الدخول مرة أخرى.", saved: "تم حفظ الحالة.", updated: "آخر تغيير", initial: "لم تتغير الحالة بعد" },
  en: { title: "Request availability", description: "Control new request intake while existing request operations continue.", noAccess: "You need the request-intake management permission.", paused: "New requests are paused", open: "New requests are open", loading: "Loading status…", note: "Optional internal reason — never shown to customers", pause: "Pause new requests", resume: "Resume new requests", saving: "Saving…", refresh: "Refresh status", error: "Could not save or load the status. Please try again.", conflict: "Another manager changed the status. Review the refreshed status before trying again.", expired: "Your session expired. Please sign in again.", saved: "Status saved.", updated: "Last change", initial: "Status has not been changed yet" },
})[locale.value]);
const session = ref<AuthSession | null>(null);
const control = ref<SalesControl | null>(null);
const reason = ref("");
const loading = ref(false);
const saving = ref(false);
const error = ref<"" | "error" | "conflict" | "expired">("");
const saved = ref(false);
const canManage = computed(() => hasPermission(session.value, "travel-sales:manage"));

async function load() {
  if (!session.value || !canManage.value || loading.value || saving.value) return;
  loading.value = true;
  error.value = "";
  saved.value = false;
  try {
    control.value = await getSalesControl(session.value.token);
    reason.value = control.value.reason ?? "";
  } catch (e) { handleError(e); control.value = null; }
  finally { loading.value = false; }
}
function handleError(e: unknown) {
  error.value = "error";
  if (e instanceof TravelMarketplaceApiError && e.status === 401) { clearAuthSession(); error.value = "expired"; }
}
async function save() {
  if (!session.value || !control.value || !canManage.value || saving.value || loading.value) return;
  saving.value = true;
  error.value = "";
  saved.value = false;
  try {
    control.value = await updateSalesControl(session.value.token, !control.value.requestsPaused, control.value.version, reason.value);
    reason.value = control.value.reason ?? "";
    saved.value = true;
    window.dispatchEvent(new CustomEvent("stg:sales-changed", { detail: { requestsOpen: !control.value.requestsPaused } }));
  } catch (e) {
    if (e instanceof TravelMarketplaceApiError && e.errorCode === "version_conflict") {
      // Reload without silently retrying a stale instruction or discarding the note.
      try { control.value = await getSalesControl(session.value.token); error.value = "conflict"; }
      catch (reloadError) { control.value = null; handleError(reloadError); }
    } else { handleError(e); }
  } finally { saving.value = false; }
}
onMounted(() => { session.value = readAuthSession(); void load(); });
</script>

<template>
  <div :dir="locale === 'ar' ? 'rtl' : 'ltr'" :lang="locale" class="max-w-3xl">
    <button type="button" class="min-h-11 rounded-wego-control border border-wego-border px-4 font-semibold" @click="locale = locale === 'ar' ? 'en' : 'ar'">{{ locale === 'ar' ? 'English' : 'العربية' }}</button>
    <h1 class="mt-6 text-3xl font-semibold">{{ text.title }}</h1>
    <p class="mt-3 text-wego-muted">{{ text.description }}</p>
    <p v-if="!canManage" role="status" class="mt-6">{{ text.noAccess }} <NuxtLink v-if="!session" to="/login" class="underline">Login</NuxtLink></p>
    <p v-else-if="loading" role="status" class="mt-6">{{ text.loading }}</p>
    <div v-else-if="control" class="mt-6 rounded-wego-card border border-wego-border bg-wego-surface p-6">
      <p role="status" class="text-lg font-semibold">{{ control.requestsPaused ? text.paused : text.open }}</p>
      <p class="mt-2 text-sm text-wego-muted">{{ control.updatedAt ? `${text.updated}: ${new Date(control.updatedAt).toLocaleString(locale)}` : text.initial }}</p>
      <label for="sales-reason" class="mt-6 block text-sm font-semibold">{{ text.note }}</label>
      <textarea id="sales-reason" v-model="reason" maxlength="300" :disabled="saving" rows="3" class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-canvas p-3" />
      <button type="button" :disabled="saving || reason.length > 300" class="mt-5 min-h-12 rounded-wego-control bg-wego-accent px-6 font-semibold text-white disabled:opacity-60" @click="save">{{ saving ? text.saving : control.requestsPaused ? text.resume : text.pause }}</button>
    </div>
    <p v-if="error" role="alert" class="mt-4 text-wego-danger">{{ text[error] }}</p>
    <p v-if="saved" role="status" class="mt-4">{{ text.saved }}</p>
    <button v-if="canManage" type="button" :disabled="loading || saving" class="mt-5 min-h-11 font-semibold underline disabled:opacity-60" @click="load">{{ text.refresh }}</button>
  </div>
</template>
