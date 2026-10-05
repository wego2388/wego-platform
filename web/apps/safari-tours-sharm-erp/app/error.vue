<script setup lang="ts">
import { WegoButton } from "@wego/ui";
import { useErpLocale } from "./composables/useErpLocale";
import ErpLanguageSwitch from "./components/ErpLanguageSwitch.vue";

const _props = defineProps<{ error: { statusCode: number; message: string } }>();
const { t, locale, direction } = useErpLocale();
useHead(() => ({ htmlAttrs: { lang: locale.value, dir: direction.value } }));
const handleError = () => clearError({ redirect: "/" });
</script>

<template>
  <main class="relative flex min-h-screen flex-col items-center justify-center bg-sts-canvas px-6 py-20 text-center">
    <div class="absolute end-4 top-4"><ErpLanguageSwitch /></div>
    <div
      class="mb-8 inline-grid size-20 place-items-center rounded-3xl bg-sts-ocean text-4xl font-black text-sts-gold select-none"
    >
      S
    </div>

    <p class="text-6xl font-bold tabular-nums text-sts-ocean">
      {{ error.statusCode }}
    </p>

    <h1 class="mt-4 text-xl font-semibold text-sts-ink">
      <template v-if="error.statusCode === 404">{{ t('error.notFound') }}</template>
      <template v-else-if="error.statusCode === 403">{{ t('error.forbidden') }}</template>
      <template v-else>{{ t('error.title') }}</template>
    </h1>

    <p class="mt-2 max-w-sm text-sm text-sts-muted">
      <template v-if="error.statusCode === 404">
        {{ t('error.notFoundText') }}
      </template>
      <template v-else-if="error.statusCode === 403">
        {{ t('error.forbiddenText') }}
      </template>
      <template v-else>
        {{ t('error.genericText') }}
      </template>
    </p>

    <div class="mt-8 flex gap-3">
      <WegoButton type="button" variant="primary" @click="handleError">
        {{ t('error.overview') }}
      </WegoButton>
      <WegoButton type="button" variant="secondary" @click="$router.back()">
        {{ t('error.back') }}
      </WegoButton>
    </div>

    <p class="mt-8 text-xs text-sts-muted">Safari Tours Sharm — {{ t('login.dashboard') }}</p>
  </main>
</template>
