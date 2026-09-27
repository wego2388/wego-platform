<script setup lang="ts">
import { WegoButton } from "@wego/ui";

const _props = defineProps<{ error: { statusCode: number; message: string } }>();
const handleError = () => clearError({ redirect: "/" });
</script>

<template>
  <main class="flex min-h-screen flex-col items-center justify-center bg-sts-canvas px-6 text-center">
    <div
      class="mb-8 inline-grid size-20 place-items-center rounded-3xl bg-sts-ocean text-4xl font-black text-sts-gold select-none"
    >
      S
    </div>

    <p class="text-6xl font-bold tabular-nums text-sts-ocean">
      {{ error.statusCode }}
    </p>

    <h1 class="mt-4 text-xl font-semibold text-sts-ink">
      <template v-if="error.statusCode === 404">Page not found</template>
      <template v-else-if="error.statusCode === 403">Access denied</template>
      <template v-else>Something went wrong</template>
    </h1>

    <p class="mt-2 max-w-sm text-sm text-sts-muted">
      <template v-if="error.statusCode === 404">
        The page you're looking for doesn't exist or has been moved.
      </template>
      <template v-else-if="error.statusCode === 403">
        You don't have permission to access this resource.
      </template>
      <template v-else>
        {{ error.message || "An unexpected error occurred. Please try again." }}
      </template>
    </p>

    <div class="mt-8 flex gap-3">
      <WegoButton type="button" variant="primary" @click="handleError">
        Go to Overview
      </WegoButton>
      <WegoButton type="button" variant="secondary" @click="$router.back()">
        Go back
      </WegoButton>
    </div>

    <p class="mt-8 text-xs text-sts-muted">Safari Tours Sharm — Operations Dashboard</p>
  </main>
</template>
