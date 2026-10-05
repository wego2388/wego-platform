<script setup lang="ts">
import type { ContentLocale, TourContentDocument } from "@wego/api-contract";
import { contentMessage } from "../utils/contentMessages";
import type { ErpLocale } from "../utils/erpLocale";
defineProps<{ document: TourContentDocument; language: ContentLocale; staffLocale: ErpLocale }>();
</script>

<template>
  <article :lang="language" :dir="language === 'ar' ? 'rtl' : 'ltr'" class="min-w-0 space-y-4 break-words">
    <h3 class="text-lg font-semibold">{{ document.name }}</h3>
    <p class="text-sm font-medium">{{ document.shortDescription }}</p>
    <p class="whitespace-pre-wrap text-sm leading-7">{{ document.description }}</p>
    <section v-for="field in (['includes', 'excludes', 'knowBeforeYouGo'] as const)" v-show="document[field]?.length" :key="field">
      <h4 class="text-sm font-semibold" :lang="staffLocale" :dir="staffLocale === 'ar' ? 'rtl' : 'ltr'">{{ contentMessage(staffLocale, field) }}</h4>
      <ul class="mt-1 list-inside list-disc space-y-1 text-sm"><li v-for="(item, index) in document[field]" :key="index">{{ item }}</li></ul>
    </section>
    <section v-if="document.meetingPoint">
      <h4 class="text-sm font-semibold" :lang="staffLocale" :dir="staffLocale === 'ar' ? 'rtl' : 'ltr'">{{ contentMessage(staffLocale, 'meetingPoint') }}</h4>
      <p class="text-sm">{{ document.meetingPoint }}</p>
    </section>
    <ol v-if="document.stops?.length" class="space-y-2">
      <li v-for="stop in document.stops" :key="stop.stopKey" class="rounded-lg border border-sts-border p-3">
        <h4 class="font-semibold">{{ stop.name }}</h4><p v-if="stop.description" class="text-sm">{{ stop.description }}</p>
      </li>
    </ol>
  </article>
</template>
