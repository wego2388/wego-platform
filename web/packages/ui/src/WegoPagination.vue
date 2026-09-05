<script setup lang="ts">
import WegoButton from "./WegoButton.vue";

// Matches the existing pattern used across every paginated ERP list
// exactly: 0-indexed `page`, "Page N" is page+1, Previous disabled at
// page 0, Next disabled once a short page proves there's no more (the
// API returns a plain page, never a total count).
defineProps<{
  page: number;
  hasNextPage: boolean;
}>();

const emit = defineEmits<{
  previous: [];
  next: [];
}>();
</script>

<template>
  <div class="flex items-center gap-3">
    <WegoButton type="button" variant="secondary" :disabled="page === 0" @click="emit('previous')">Previous</WegoButton>
    <span class="text-sm text-wego-muted">Page {{ page + 1 }}</span>
    <WegoButton type="button" variant="secondary" :disabled="!hasNextPage" @click="emit('next')">Next</WegoButton>
  </div>
</template>
