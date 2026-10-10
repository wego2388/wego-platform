<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { getPublicSalesStatus } from "../composables/useSalesControlApi";

const requestsOpen = ref<boolean | null>(null);
const failed = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;
let generation = 0;
async function refresh() {
  const own = ++generation;
  try {
    const open = await getPublicSalesStatus();
    if (own === generation) { requestsOpen.value = open; failed.value = false; }
  } catch {
    if (own === generation) { requestsOpen.value = null; failed.value = true; }
  }
}
function changed(event: Event) {
  const open = (event as CustomEvent<{ requestsOpen: boolean }>).detail?.requestsOpen;
  if (typeof open === "boolean") { ++generation; requestsOpen.value = open; failed.value = false; }
  else void refresh();
}
onMounted(() => {
  void refresh();
  timer = setInterval(() => { void refresh(); }, 30_000);
  window.addEventListener("focus", refresh);
  window.addEventListener("stg:sales-changed", changed);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
  ++generation;
  window.removeEventListener("focus", refresh);
  window.removeEventListener("stg:sales-changed", changed);
});
</script>

<template>
  <aside v-if="requestsOpen === false || failed" role="status" class="mb-6 rounded-wego-card border border-wego-border bg-wego-surface p-4 text-sm text-wego-ink">
    <template v-if="requestsOpen === false">
      <p lang="ar" dir="rtl">استقبال طلبات جديدة متوقف. متابعة الطلبات الحالية مستمرة.</p>
      <p lang="en">New requests are paused. Existing request operations remain available.</p>
    </template>
    <template v-else>
      <p lang="ar" dir="rtl">تعذر التحقق من حالة استقبال الطلبات.</p>
      <p lang="en">Could not verify request availability.</p>
    </template>
  </aside>
</template>
