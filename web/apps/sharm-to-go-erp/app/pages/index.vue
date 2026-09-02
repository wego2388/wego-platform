<script setup lang="ts">
import { computed, ref } from "vue";
import { dashboardCopy, dashboardDirection, type DashboardLocale } from "../content/dashboard";

const locale = ref<DashboardLocale>("en");
const copy = computed(() => dashboardCopy[locale.value]);
const direction = computed(() => dashboardDirection(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "أساس العمليات · Sharm To Go" : "Operations foundation · Sharm To Go",
  htmlAttrs: {
    dir: direction.value,
    lang: locale.value,
  },
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas text-sharm-ink lg:grid lg:grid-cols-[17rem_1fr]">
    <aside class="border-b border-sharm-border bg-sharm-ink px-6 py-6 text-white lg:min-h-screen lg:border-e lg:border-b-0">
      <div class="flex items-center gap-3">
        <span class="grid size-11 place-items-center rounded-2xl bg-sharm-lagoon font-black text-sharm-sea">S</span>
        <div>
          <p class="font-semibold">Sharm To Go</p>
          <p class="text-xs text-white/60">Wego Travel Marketplace</p>
        </div>
      </div>
      <nav class="mt-10 grid gap-2" aria-label="Dashboard navigation">
        <span
          v-for="(item, index) in copy.nav"
          :key="item"
          class="rounded-xl px-4 py-3 text-sm font-semibold"
          :class="index === 0 ? 'bg-white/12 text-white' : 'text-white/55'"
        >
          {{ item }}
        </span>
      </nav>
    </aside>

    <section class="px-6 py-8 sm:px-10 lg:px-12 lg:py-10">
      <header class="flex flex-wrap items-start justify-between gap-5">
        <div>
          <span class="inline-flex rounded-full bg-sharm-warning-soft px-4 py-2 text-xs font-bold tracking-[0.1em] text-sharm-warning uppercase">
            {{ copy.foundationMode }}
          </span>
          <h1 class="mt-5 text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.title }}</h1>
          <p class="mt-3 max-w-3xl leading-7 text-sharm-muted">{{ copy.subtitle }}</p>
        </div>
        <button type="button" class="rounded-full border border-sharm-border bg-white px-4 py-2 text-sm font-semibold text-sharm-sea" @click="toggleLocale">
          {{ copy.languageName }}
        </button>
      </header>

      <nav class="mt-6 flex flex-wrap gap-4" aria-label="Catalog operations">
        <NuxtLink to="/login" class="text-sm font-semibold text-sharm-sea underline">Sign in</NuxtLink>
        <NuxtLink to="/providers" class="text-sm font-semibold text-sharm-sea underline">Providers</NuxtLink>
        <NuxtLink to="/categories" class="text-sm font-semibold text-sharm-sea underline">Categories</NuxtLink>
        <NuxtLink to="/services" class="text-sm font-semibold text-sharm-sea underline">Services</NuxtLink>
      </nav>

      <div class="mt-8 rounded-2xl border border-amber-200 bg-sharm-warning-soft p-5 text-sharm-warning" role="status">
        <p class="font-semibold">{{ copy.noticeTitle }}</p>
        <p class="mt-2 text-sm leading-6">{{ copy.noticeBody }}</p>
      </div>

      <section class="mt-10">
        <h2 class="text-xl font-semibold">{{ copy.readiness }}</h2>
        <div class="mt-5 grid gap-4 xl:grid-cols-2">
          <article v-for="(module, index) in copy.modules" :key="module.name" class="rounded-2xl border border-sharm-border bg-white p-5 shadow-sm" :class="index === 0 ? 'xl:col-span-2' : ''">
            <div class="flex items-start justify-between gap-4">
              <h3 class="font-semibold">{{ module.name }}</h3>
              <span class="shrink-0 rounded-full px-3 py-1 text-xs font-bold" :class="index === 0 ? 'bg-sharm-lagoon text-sharm-sea' : 'bg-sharm-canvas text-sharm-muted'">
                {{ module.status }}
              </span>
            </div>
            <p class="mt-3 text-sm leading-6 text-sharm-muted">{{ module.detail }}</p>
          </article>
        </div>
      </section>

      <div class="mt-10 grid gap-5 xl:grid-cols-2">
        <section class="rounded-2xl border border-sharm-border bg-white p-6">
          <h2 class="text-xl font-semibold">{{ copy.decisions }}</h2>
          <ul class="mt-5 grid gap-3 text-sm text-sharm-muted">
            <li v-for="item in copy.decisionItems" :key="item" class="flex gap-3">
              <span class="text-sharm-sea">○</span><span>{{ item }}</span>
            </li>
          </ul>
        </section>
        <section class="rounded-2xl bg-sharm-sea p-6 text-white">
          <h2 class="text-xl font-semibold">{{ copy.nextGate }}</h2>
          <p class="mt-4 leading-7 text-white/75">{{ copy.nextGateBody }}</p>
        </section>
      </div>
    </section>
  </main>
</template>
