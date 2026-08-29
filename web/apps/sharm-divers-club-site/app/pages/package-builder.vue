<script setup lang="ts">
import { computed, ref } from "vue";
import SiteFooter from "../components/SiteFooter.vue";
import SiteHeader from "../components/SiteHeader.vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "../content/locales";
import { formatEur, offerings } from "../content/offerings";

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

const selectedCodes = ref<string[]>([]);

function isSelected(code: string) {
  return selectedCodes.value.includes(code);
}

function toggle(code: string) {
  selectedCodes.value = isSelected(code) ? selectedCodes.value.filter(item => item !== code) : [...selectedCodes.value, code];
}

const selectedOfferings = computed(() => offerings.filter(offering => selectedCodes.value.includes(offering.code)));
const total = computed(() => selectedOfferings.value.reduce((sum, offering) => sum + offering.priceEur, 0));

const askUrl = computed(() => {
  const lines = selectedOfferings.value.map(offering => `- ${offering.name[locale.value]} (${offering.code}) — ${formatEur(offering.priceEur)}`);
  const intro = locale.value === "ar" ? "مرحبًا، عايز أسأل عن الباقة دي:" : "Hi, I'd like to ask about this package:";
  const totalLine = locale.value === "ar"
    ? `الإجمالي التقديري: ${formatEur(total.value)}`
    : `Estimated total: ${formatEur(total.value)}`;
  const text = [intro, ...lines, totalLine].join("\n");
  return `${whatsappUrl}?text=${encodeURIComponent(text)}`;
});

useHead(() => ({
  title: locale.value === "ar" ? "كوّن باقتك · Sharm Divers Club" : "Package Builder · Sharm Divers Club",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "description", content: copy.value.packageBuilder.body }],
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}
</script>

<template>
  <main id="main-content" tabindex="-1" :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas text-sdc-ink">
    <SiteHeader
      variant="solid"
      :locale="locale"
      :direction="direction"
      :home-label="copy.nav.home"
      :discover-label="copy.nav.discover"
      :about-label="copy.nav.about"
      :faq-label="copy.nav.faq"
      :contact-label="copy.nav.contact"
      :language-name="copy.languageName"
      :whatsapp-label="copy.whatsappFab"
      :menu-label="copy.nav.menu"
      @toggle-locale="toggleLocale"
    />

    <section class="px-6 pt-10 pb-4 lg:px-10">
      <div class="mx-auto max-w-3xl rounded-[2rem] border border-sdc-border bg-sdc-surface p-8 text-center shadow-sm sm:p-12">
        <h1 class="font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.packageBuilder.heading }}</h1>
        <p class="mx-auto mt-5 max-w-2xl leading-8 text-sdc-muted">{{ copy.packageBuilder.body }}</p>
        <NuxtLink to="/dive-sites" class="mt-6 inline-flex text-sm font-semibold text-sdc-deep-bright hover:underline">
          {{ copy.packageBuilder.exploreSitesCta }}
        </NuxtLink>
      </div>
    </section>

    <section class="mx-auto grid max-w-6xl gap-8 px-6 py-10 lg:grid-cols-[1fr_20rem] lg:px-10">
      <div class="grid gap-3">
        <div
          v-for="offering in offerings"
          :key="offering.code"
          class="flex items-center justify-between gap-4 rounded-2xl border border-sdc-border bg-sdc-surface p-4"
        >
          <div>
            <p class="font-semibold">{{ offering.name[locale] }}</p>
            <p class="money mt-1 text-sm text-sdc-deep-bright">{{ formatEur(offering.priceEur) }}</p>
          </div>
          <button
            type="button"
            class="min-h-10 shrink-0 rounded-full border px-4 text-sm font-semibold transition-colors"
            :class="isSelected(offering.code) ? 'border-sdc-deep bg-sdc-deep text-white' : 'border-sdc-border bg-sdc-canvas text-sdc-deep-bright'"
            :aria-pressed="isSelected(offering.code)"
            @click="toggle(offering.code)"
          >
            {{ isSelected(offering.code) ? copy.packageBuilder.addedLabel : copy.packageBuilder.addLabel }}
          </button>
        </div>
      </div>

      <aside class="h-fit rounded-[1.75rem] border border-sdc-border bg-sdc-surface p-6 shadow-sm">
        <p v-if="!selectedOfferings.length" class="text-sm leading-6 text-sdc-muted">{{ copy.packageBuilder.emptyLabel }}</p>
        <template v-else>
          <ul class="grid gap-3">
            <li v-for="offering in selectedOfferings" :key="offering.code" class="flex items-center justify-between gap-3 text-sm">
              <span>{{ offering.name[locale] }}</span>
              <button type="button" class="font-semibold text-sdc-deep-bright hover:underline" @click="toggle(offering.code)">
                {{ copy.packageBuilder.removeLabel }}
              </button>
            </li>
          </ul>
          <div class="mt-6 border-t border-sdc-border pt-6">
            <p class="text-xs font-semibold text-sdc-deep-bright uppercase">{{ copy.packageBuilder.totalLabel }}</p>
            <p class="money mt-1 text-3xl font-semibold text-sdc-ink">{{ formatEur(total) }}</p>
          </div>
          <a :href="askUrl" target="_blank" rel="noopener" class="mt-5 flex min-h-11 items-center justify-center rounded-full bg-sdc-deep px-5 text-sm font-semibold text-white">
            {{ copy.packageBuilder.whatsapp }}
          </a>
        </template>
      </aside>
    </section>

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
