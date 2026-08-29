<script setup lang="ts">
import { WegoFoundationCard } from "@wego/ui";

useHead({
  title: "Wego Platform",
  meta: [
    {
      name: "description",
      content: "Product-neutral Wego operations workspace foundation",
    },
  ],
});

const foundationItems = [
  {
    title: "Secure by default",
    state: "ready" as const,
    description: "Health is public; every business route remains denied until explicitly authorized.",
  },
  {
    title: "Versioned contracts",
    state: "ready" as const,
    description: "Web clients integrate through the validated Wego OpenAPI contract.",
  },
  {
    title: "Identity foundation",
    state: "ready" as const,
    description: "Real login issues a session; permissions are resolved from assigned roles.",
  },
  {
    title: "Business modules",
    state: "ready" as const,
    description: "Wego Divers bookings are live — see Offerings and Bookings above.",
  },
];
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">
      <header class="flex max-w-3xl flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">
            Wego Platform
          </p>
          <h1 class="mt-4 text-4xl font-semibold tracking-tight sm:text-6xl">
            A calm foundation for serious operations.
          </h1>
          <p class="mt-6 text-lg leading-8 text-wego-muted">
            This product-neutral shell proves the shared web boundary. It contains no client-specific
            workflows and makes no authorization decisions.
          </p>
        </div>
        <div class="flex shrink-0 flex-wrap items-center gap-4">
          <NuxtLink to="/offerings" class="text-sm font-semibold text-wego-accent underline">Offerings</NuxtLink>
          <NuxtLink to="/bookings" class="text-sm font-semibold text-wego-accent underline">Bookings</NuxtLink>
          <NuxtLink to="/divers" class="text-sm font-semibold text-wego-accent underline">Divers</NuxtLink>
          <NuxtLink to="/equipment" class="text-sm font-semibold text-wego-accent underline">Equipment</NuxtLink>
          <NuxtLink to="/boat-charters" class="text-sm font-semibold text-wego-accent underline">Boat Charters</NuxtLink>
          <NuxtLink
            to="/login"
            class="rounded-wego-control bg-wego-accent px-5 py-2.5 text-sm font-semibold whitespace-nowrap text-white transition-opacity hover:opacity-90"
          >
            Sign in
          </NuxtLink>
        </div>
      </header>

      <section aria-labelledby="foundation-status" class="mt-14">
        <h2 id="foundation-status" class="text-xl font-semibold">Foundation status</h2>
        <div class="mt-6 grid gap-5 md:grid-cols-3">
          <WegoFoundationCard
            v-for="item in foundationItems"
            :key="item.title"
            :title="item.title"
            :state="item.state"
            :description="item.description"
          />
        </div>
      </section>
    </div>
  </main>
</template>
