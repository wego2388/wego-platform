<script setup lang="ts">
import type { Section } from "../../content/info";

/** A titled document of sections (terms, privacy, about). */
defineProps<{ title: string; intro?: string; updatedLabel?: string; updated?: string; sections: Section[] }>();
</script>

<template>
  <main id="main-content" tabindex="-1">
    <div class="bg-sts-ocean px-4 pt-12 pb-10 text-white sm:px-6 lg:px-10">
      <div class="mx-auto max-w-3xl">
        <h1 class="font-display text-3xl font-semibold sm:text-4xl">{{ title }}</h1>
        <p v-if="intro" class="mt-4 text-lg leading-8 text-white/85">{{ intro }}</p>
        <p v-if="updated" class="mt-3 text-sm text-white/70">{{ updatedLabel }}: <time :datetime="updated">{{ updated }}</time></p>
      </div>
    </div>
    <article class="mx-auto grid max-w-3xl gap-8 px-4 py-12 sm:px-6">
      <section v-for="(section, i) in sections" :id="`section-${i + 1}`" :key="section.heading" class="scroll-mt-24" :aria-labelledby="`section-${i + 1}-heading`">
        <h2 :id="`section-${i + 1}-heading`" class="text-xl font-semibold">{{ section.heading }}</h2>
        <p v-for="paragraph in section.body" :key="paragraph" class="mt-3 leading-7 text-sts-ink/85">{{ paragraph }}</p>
      </section>
      <slot />
    </article>
  </main>
</template>
