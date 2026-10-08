<script setup lang="ts">
import { computed, ref } from "vue";
import { useErpLocale } from "../composables/useErpLocale";
import { activeStaffLink, groupedStaffLinks, type StaffLink } from "../utils/staffNavigation";
const props = defineProps<{ links: StaffLink[]; searchId: string }>();
const route = useRoute();
const { t } = useErpLocale();
const query = ref("");
const searchInput = ref<HTMLInputElement | null>(null);
const groups = computed(() => groupedStaffLinks(props.links, query.value));
function clearQuery() {
  query.value = "";
  searchInput.value?.focus();
}
</script>

<template>
  <nav :aria-label="t('shell.navigation')" class="staff-navigation">
    <div class="mb-5 grid gap-2">
      <label :for="searchId" class="text-xs font-semibold text-sts-muted">{{ t('workspace.search') }}</label>
      <div class="flex items-center gap-1">
        <input :id="searchId" ref="searchInput" v-model="query" type="search" maxlength="80" autocomplete="off" class="min-w-0 w-full rounded-xl border border-sts-border bg-sts-canvas px-3 py-2.5 text-sm" dir="auto">
        <button v-if="query" type="button" class="min-h-11 shrink-0 rounded-lg px-2 text-sts-ink" :aria-label="t('workspace.clear')" @click="clearQuery">×</button>
      </div>
    </div>
    <p v-if="groups.length === 0" role="status" class="py-4 text-sm text-sts-muted">{{ t('workspace.noMatch') }}</p>
    <section v-for="group in groups" :key="group.key" class="mb-5 last:mb-0">
      <h2 class="mb-2 px-3 text-xs font-bold tracking-wide text-sts-muted">{{ t(group.key) }}</h2>
      <ul class="grid gap-1">
        <li v-for="link in group.links" :key="link.to">
          <NuxtLink
            :to="link.to" class="flex min-h-11 items-center rounded-xl border-s-4 px-3 py-2 text-sm font-semibold transition-colors"
            :class="activeStaffLink(route.path, link.to) ? 'border-sts-gold bg-sts-ocean text-white shadow-sm' : 'border-transparent text-sts-ink hover:bg-sts-canvas'"
            :aria-current="activeStaffLink(route.path, link.to) ? 'page' : undefined">{{ link.label }}</NuxtLink>
        </li>
      </ul>
    </section>
  </nav>
</template>
