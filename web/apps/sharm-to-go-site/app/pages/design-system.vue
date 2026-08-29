<script setup lang="ts">
import { computed, ref } from "vue";
import { directionFor, type SharmLocale } from "../content/locales";

const locale = ref<SharmLocale>("en");
const direction = computed(() => directionFor(locale.value));

const copy = computed(() => locale.value === "en" ? {
  switchLanguage: "العربية",
  back: "Back to experiences",
  eyebrow: "Living design system · 0.1.0",
  title: "The visual contract behind Sharm To Go",
  body: "This route proves the semantic tokens and reusable states used by the booking foundation. It contains no live business data.",
  colors: "Semantic color",
  type: "Typography and hierarchy",
  controls: "Controls",
  states: "Operational states",
  display: "A calm headline for a clear Sharm experience.",
  paragraph: "Details stay readable before the customer chooses a date, pays or confirms.",
  primary: "Primary action",
  secondary: "Secondary action",
  field: "Hotel or pickup point",
  statuses: ["Awaiting confirmation", "Paid", "Payment failed", "Refunded"],
} : {
  switchLanguage: "English",
  back: "العودة للتجارب",
  eyebrow: "نظام التصميم الحي · 0.1.0",
  title: "العقد البصري وراء Sharm To Go",
  body: "الصفحة تثبت الـTokens والحالات القابلة لإعادة الاستخدام في أساس الحجز، ولا تحتوي على بيانات أعمال فعلية.",
  colors: "الألوان الوظيفية",
  type: "الخطوط والتدرج البصري",
  controls: "عناصر التحكم",
  states: "حالات التشغيل",
  display: "عنوان هادئ لتجربة شرم واضحة.",
  paragraph: "تظل التفاصيل سهلة القراءة قبل اختيار التاريخ أو الدفع أو التأكيد.",
  primary: "الإجراء الأساسي",
  secondary: "إجراء ثانوي",
  field: "الفندق أو نقطة الاستلام",
  statuses: ["بانتظار التأكيد", "مدفوع", "فشل الدفع", "تم الاسترجاع"],
});

const swatches = [
  { name: "Sea / primary", className: "bg-sharm-sea text-white", value: "#075f67" },
  { name: "Lagoon / selected", className: "bg-sharm-lagoon text-sharm-ink", value: "#d8f1ef" },
  { name: "Sand / warmth", className: "bg-sharm-sand text-sharm-ink", value: "#f4dec0" },
  { name: "Sun / focus", className: "bg-sharm-sun text-sharm-ink", value: "#f2a93b" },
  { name: "Canvas", className: "bg-sharm-canvas text-sharm-ink", value: "#f7fbfa" },
  { name: "Ink", className: "bg-sharm-ink text-white", value: "#102f35" },
];

useHead(() => ({
  title: locale.value === "ar" ? "نظام التصميم · Sharm To Go" : "Design system · Sharm To Go",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "robots", content: "noindex,nofollow" }],
}));
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-5 py-6 text-sharm-ink lg:px-10">
    <header class="mx-auto flex max-w-7xl items-center justify-between gap-4">
      <NuxtLink to="/experiences" class="font-semibold text-sharm-sea">{{ direction === "rtl" ? "→" : "←" }} {{ copy.back }}</NuxtLink>
      <button type="button" class="min-h-11 rounded-full border border-sharm-border bg-white px-4 text-sm font-semibold" @click="locale = locale === 'en' ? 'ar' : 'en'">
        {{ copy.switchLanguage }}
      </button>
    </header>

    <section class="mx-auto max-w-7xl py-14">
      <span class="text-xs font-bold tracking-[0.12em] text-sharm-sea uppercase">{{ copy.eyebrow }}</span>
      <h1 class="mt-4 max-w-4xl text-4xl font-semibold tracking-tight sm:text-6xl">{{ copy.title }}</h1>
      <p class="mt-5 max-w-3xl text-lg leading-8 text-sharm-muted">{{ copy.body }}</p>
    </section>

    <div class="mx-auto grid max-w-7xl gap-6 pb-16 lg:grid-cols-2">
      <section class="rounded-[1.75rem] border border-sharm-border bg-white p-6">
        <h2 class="text-xl font-semibold">{{ copy.colors }}</h2>
        <div class="mt-5 grid gap-3 sm:grid-cols-2">
          <div v-for="swatch in swatches" :key="swatch.name" class="overflow-hidden rounded-2xl border border-sharm-border">
            <div class="h-20 p-4 font-semibold" :class="swatch.className">{{ swatch.name }}</div>
            <code class="reference block bg-white p-3 text-sm">{{ swatch.value }}</code>
          </div>
        </div>
      </section>

      <section class="rounded-[1.75rem] border border-sharm-border bg-white p-6">
        <h2 class="text-xl font-semibold">{{ copy.type }}</h2>
        <p class="mt-7 text-4xl leading-tight font-semibold">{{ copy.display }}</p>
        <p class="mt-5 max-w-xl leading-7 text-sharm-muted">{{ copy.paragraph }}</p>
        <p class="reference mt-8 text-sm text-sharm-muted">Booking STG-2026-0001 · EGP 1,450</p>
      </section>

      <section class="rounded-[1.75rem] border border-sharm-border bg-white p-6">
        <h2 class="text-xl font-semibold">{{ copy.controls }}</h2>
        <div class="mt-6 flex flex-wrap gap-3">
          <button type="button" class="min-h-11 rounded-full bg-sharm-sea px-5 font-semibold text-white">{{ copy.primary }}</button>
          <button type="button" class="min-h-11 rounded-full border border-sharm-border px-5 font-semibold text-sharm-sea">{{ copy.secondary }}</button>
        </div>
        <label class="mt-6 grid gap-2 text-sm font-semibold">
          {{ copy.field }}
          <input type="text" class="min-h-12 rounded-xl border border-sharm-border px-4 font-normal">
        </label>
      </section>

      <section class="rounded-[1.75rem] border border-sharm-border bg-white p-6">
        <h2 class="text-xl font-semibold">{{ copy.states }}</h2>
        <div class="mt-6 grid gap-3 sm:grid-cols-2">
          <span class="rounded-full bg-sharm-warning-soft px-4 py-3 text-sm font-semibold text-sharm-warning">○ {{ copy.statuses[0] }}</span>
          <span class="rounded-full bg-sharm-success-soft px-4 py-3 text-sm font-semibold text-sharm-success">✓ {{ copy.statuses[1] }}</span>
          <span class="rounded-full bg-sharm-danger-soft px-4 py-3 text-sm font-semibold text-sharm-danger">! {{ copy.statuses[2] }}</span>
          <span class="rounded-full bg-sharm-info-soft px-4 py-3 text-sm font-semibold text-sharm-info">↺ {{ copy.statuses[3] }}</span>
        </div>
      </section>
    </div>
  </main>
</template>
