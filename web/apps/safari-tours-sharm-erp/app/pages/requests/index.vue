<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { OnlineRequest, OnlineRequestStatus } from "@wego/api-contract";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../../composables/useAuthSession";
import { ToursApiError } from "../../composables/useToursApi";
import { listOnlineRequests } from "../../composables/useOnlineRequests";
import { useErpLocale } from "../../composables/useErpLocale";
import { requestMessages } from "../../utils/onlineRequestMessages";

const { locale, dateLabel, instantLabel, money } = useErpLocale();
const c = computed(() => requestMessages[locale.value]);
useHead(() => ({ title: `${c.value.title} · Safari Tours Sharm` }));
const session = ref<AuthSession | null>(null);
const items = ref<OnlineRequest[]>([]);
const status = ref<OnlineRequestStatus | "">("NEW");
const page = ref(0); const loading = ref(false); const failed = ref(false);
let sequence = 0; let timer: ReturnType<typeof setInterval> | undefined;
const tourName = (item: OnlineRequest) => item.tour?.nameEn ?? item.tour?.slug ?? item.tourId;
async function load() {
  if (!session.value) return;
  const seq = ++sequence; loading.value = true;
  try {
    const result = await listOnlineRequests(session.value.token, status.value, page.value);
    if (seq !== sequence) return;
    items.value = result; failed.value = false;
  } catch (error) {
    if (error instanceof ToursApiError && error.status === 401) { clearAuthSession(); void navigateTo("/login"); }
    if (seq === sequence) failed.value = true;
  }
  finally { if (seq === sequence) loading.value = false; }
}
watch(status, () => { page.value = 0; void load(); });
watch(page, () => { void load(); });
onMounted(async () => {
  session.value = readAuthSession();
  if (!session.value) { await navigateTo("/login"); return; }
  if (!hasPermission(session.value, "tours-operator.booking:view")) { await navigateTo("/"); return; }
  await load();
  timer = setInterval(() => { if (!document.hidden && !loading.value) void load(); }, 20_000);
});
onBeforeUnmount(() => { sequence++; clearInterval(timer); });
</script>

<template>
  <main class="mx-auto max-w-6xl space-y-5 px-4 py-8 sm:px-8 lg:px-12">
    <header><h1 class="text-2xl font-bold">{{ c.title }}</h1><p class="mt-2 max-w-3xl text-sm leading-relaxed text-sts-muted">{{ c.help }}</p></header>
    <div class="flex flex-wrap items-center gap-3 rounded-2xl border border-sts-border bg-sts-surface p-4">
      <label class="grid gap-1 text-sm font-semibold">{{ c.all }}<select v-model="status" class="min-h-11 rounded-xl border border-sts-border bg-sts-surface px-3"><option value="">{{ c.all }}</option><option v-for="s in (['NEW', 'IN_PROGRESS', 'CONVERTED', 'CLOSED'] as const)" :key="s" :value="s">{{ c[s] }}</option></select></label>
      <button type="button" class="min-h-11 rounded-xl border border-sts-border px-4 font-semibold" :disabled="loading" @click="load">{{ c.refresh }}</button><p class="text-xs text-sts-muted">{{ c.auto }}</p>
    </div>
    <p v-if="failed" role="alert" class="text-sts-danger">{{ c.failed }}</p>
    <p v-if="loading && !items.length" role="status">{{ c.loading }}</p>
    <p v-else-if="!items.length && !failed" class="rounded-2xl bg-sts-surface p-8 text-center text-sts-muted">{{ c.empty }}</p>
    <div class="grid gap-4 md:grid-cols-2">
      <article v-for="item in items" :key="item.id" class="min-w-0 rounded-2xl border border-sts-border bg-sts-surface p-5">
        <div class="flex items-start justify-between gap-3"><h2 class="min-w-0 break-words font-bold">{{ tourName(item) }}</h2><span class="shrink-0 rounded-lg bg-sts-sand-soft px-2 py-1 text-xs font-semibold">{{ c[item.status] }}</span></div>
        <p class="mt-2 break-all font-mono text-xs" dir="ltr">{{ item.reference }}</p>
        <dl class="mt-4 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm"><dt class="text-sts-muted">{{ c.name }}</dt><dd class="break-words">{{ item.customer.fullName }}</dd><dt class="text-sts-muted">{{ c.preferred }}</dt><dd>{{ dateLabel(item.preferredDate) }}</dd><dt class="text-sts-muted">{{ c.guests }}</dt><dd>{{ item.adultsCount }} + {{ item.childrenCount }}</dd><dt class="text-sts-muted">{{ c.estimate }}</dt><dd>{{ money(item.estimatedTotal) }}</dd></dl>
        <div class="mt-4 flex flex-wrap items-center justify-between gap-2"><span class="text-xs text-sts-muted">{{ instantLabel(item.createdAt) }}</span><NuxtLink :to="`/requests/${item.id}`" class="rounded-xl bg-sts-ocean px-4 py-2 font-semibold text-white">{{ c.open }}</NuxtLink></div>
      </article>
    </div>
    <nav class="flex items-center justify-between gap-3" :aria-label="c.title"><button type="button" :disabled="page === 0 || loading" class="min-h-11 rounded-xl border border-sts-border px-4 disabled:opacity-40" @click="page--">{{ c.previous }}</button><span>{{ page + 1 }}</span><button type="button" :disabled="items.length < 50 || loading" class="min-h-11 rounded-xl border border-sts-border px-4 disabled:opacity-40" @click="page++">{{ c.next }}</button></nav>
  </main>
</template>
