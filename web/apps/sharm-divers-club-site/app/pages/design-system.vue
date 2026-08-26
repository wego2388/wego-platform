<script setup lang="ts">
import { computed, ref } from "vue";
import { directionFor, type SdcLocale } from "../content/locales";

const locale = ref<SdcLocale>("en");
const direction = computed(() => directionFor(locale.value));

const copy = computed(() => locale.value === "en" ? {
  switchLanguage: "العربية",
  back: "Back to discover",
  eyebrow: "Living design system · 0.1.0",
  title: "The visual contract behind Sharm Divers Club",
  body: "This route proves the semantic tokens and reusable states used by the public site. It contains no live business or pricing data.",
  colors: "Semantic color",
  type: "Typography and hierarchy",
  controls: "Controls",
  states: "Operational states",
  display: "A calm, confident headline for the Red Sea.",
  paragraph: "Details stay readable before a customer messages us or a category is chosen.",
  primary: "Primary action",
  secondary: "Secondary action",
  field: "Full name",
  statuses: ["Preview — pricing pending", "Approved fact", "Not yet approved", "Owner review required"],
} : {
  switchLanguage: "English",
  back: "العودة للاستكشاف",
  eyebrow: "نظام التصميم الحي · 0.1.0",
  title: "العقد البصري وراء Sharm Divers Club",
  body: "الصفحة تثبت الـTokens والحالات القابلة لإعادة الاستخدام في الموقع العام، ولا تحتوي على بيانات أعمال أو أسعار فعلية.",
  colors: "الألوان الوظيفية",
  type: "الخطوط والتدرج البصري",
  controls: "عناصر التحكم",
  states: "حالات التشغيل",
  display: "عنوان هادئ وواثق للبحر الأحمر.",
  paragraph: "تظل التفاصيل سهلة القراءة قبل ما العميل يراسلنا أو يختار فئة.",
  primary: "الإجراء الأساسي",
  secondary: "إجراء ثانوي",
  field: "الاسم الكامل",
  statuses: ["معاينة — السعر لسه مش معتمد", "حقيقة معتمدة", "مش معتمد بعد", "يحتاج مراجعة المالك"],
});

const swatches = [
  { name: "Deep ocean / primary", className: "bg-sdc-deep text-white", value: "#0a3a4a" },
  { name: "Turquoise soft / selected", className: "bg-sdc-turquoise-soft text-sdc-ink", value: "#d9f1f1" },
  { name: "Sand / warmth", className: "bg-sdc-sand text-white", value: "#c9975a" },
  { name: "Sand soft / focus", className: "bg-sdc-sand-soft text-sdc-ink", value: "#f1e1c3" },
  { name: "Canvas", className: "bg-sdc-canvas text-sdc-ink", value: "#faf6ee" },
  { name: "Ink", className: "bg-sdc-ink text-white", value: "#0b2027" },
];

useHead(() => ({
  title: locale.value === "ar" ? "نظام التصميم · Sharm Divers Club" : "Design system · Sharm Divers Club",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "robots", content: "noindex,nofollow" }],
}));
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas px-5 py-6 text-sdc-ink lg:px-10">
    <header class="mx-auto flex max-w-7xl items-center justify-between gap-4">
      <NuxtLink to="/discover" class="font-semibold text-sdc-deep-bright">{{ direction === "rtl" ? "→" : "←" }} {{ copy.back }}</NuxtLink>
      <button type="button" class="min-h-11 rounded-full border border-sdc-border bg-white px-4 text-sm font-semibold" @click="locale = locale === 'en' ? 'ar' : 'en'">
        {{ copy.switchLanguage }}
      </button>
    </header>

    <section class="mx-auto max-w-7xl py-14">
      <span class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.eyebrow }}</span>
      <h1 class="mt-4 max-w-4xl font-display text-4xl font-semibold tracking-tight sm:text-6xl">{{ copy.title }}</h1>
      <p class="mt-5 max-w-3xl text-lg leading-8 text-sdc-muted">{{ copy.body }}</p>
    </section>

    <div class="mx-auto grid max-w-7xl gap-6 pb-16 lg:grid-cols-2">
      <section class="rounded-[1.75rem] border border-sdc-border bg-white p-6">
        <h2 class="text-xl font-semibold">{{ copy.colors }}</h2>
        <div class="mt-5 grid gap-3 sm:grid-cols-2">
          <div v-for="swatch in swatches" :key="swatch.name" class="overflow-hidden rounded-2xl border border-sdc-border">
            <div class="h-20 p-4 font-semibold" :class="swatch.className">{{ swatch.name }}</div>
            <code class="reference block bg-white p-3 text-sm">{{ swatch.value }}</code>
          </div>
        </div>
      </section>

      <section class="rounded-[1.75rem] border border-sdc-border bg-white p-6">
        <h2 class="text-xl font-semibold">{{ copy.type }}</h2>
        <p class="mt-7 font-display text-4xl leading-tight font-semibold">{{ copy.display }}</p>
        <p class="mt-5 max-w-xl leading-7 text-sdc-muted">{{ copy.paragraph }}</p>
        <p class="reference mt-8 text-sm text-sdc-muted">CDWS #100601 · +20 10 6646 1010</p>
      </section>

      <section class="rounded-[1.75rem] border border-sdc-border bg-white p-6">
        <h2 class="text-xl font-semibold">{{ copy.controls }}</h2>
        <div class="mt-6 flex flex-wrap gap-3">
          <button type="button" class="min-h-11 rounded-full bg-sdc-deep px-5 font-semibold text-white">{{ copy.primary }}</button>
          <button type="button" class="min-h-11 rounded-full border border-sdc-border px-5 font-semibold text-sdc-deep-bright">{{ copy.secondary }}</button>
        </div>
        <label class="mt-6 grid gap-2 text-sm font-semibold">
          {{ copy.field }}
          <input type="text" class="min-h-12 rounded-xl border border-sdc-border px-4 font-normal">
        </label>
      </section>

      <section class="rounded-[1.75rem] border border-sdc-border bg-white p-6">
        <h2 class="text-xl font-semibold">{{ copy.states }}</h2>
        <div class="mt-6 grid gap-3 sm:grid-cols-2">
          <span class="rounded-full bg-sdc-warning-soft px-4 py-3 text-sm font-semibold text-sdc-warning">○ {{ copy.statuses[0] }}</span>
          <span class="rounded-full bg-sdc-success-soft px-4 py-3 text-sm font-semibold text-sdc-success">✓ {{ copy.statuses[1] }}</span>
          <span class="rounded-full bg-sdc-danger-soft px-4 py-3 text-sm font-semibold text-sdc-danger">! {{ copy.statuses[2] }}</span>
          <span class="rounded-full bg-sdc-info-soft px-4 py-3 text-sm font-semibold text-sdc-info">↺ {{ copy.statuses[3] }}</span>
        </div>
      </section>
    </div>
  </main>
</template>
