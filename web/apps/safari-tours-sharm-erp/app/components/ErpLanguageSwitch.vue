<script setup lang="ts">
import { useErpLocale } from "../composables/useErpLocale";

const { locale, setLocale, t } = useErpLocale();
</script>

<template>
  <div class="inline-flex shrink-0 gap-1 rounded-lg border border-current/25 p-1 text-sm print:hidden" role="group" :aria-label="t('shell.language')">
    <button type="button" lang="en" class="rounded-md px-2 py-1 font-semibold focus-visible:outline-current" :class="locale === 'en' ? 'bg-current/10 underline underline-offset-4' : 'opacity-75 hover:opacity-100'" :aria-pressed="locale === 'en'" @click="setLocale('en')">EN</button>
    <button type="button" lang="ar" class="rounded-md px-2 py-1 font-semibold focus-visible:outline-current" :class="locale === 'ar' ? 'bg-current/10 underline underline-offset-4' : 'opacity-75 hover:opacity-100'" :aria-pressed="locale === 'ar'" @click="setLocale('ar')">العربية</button>
  </div>
</template>
