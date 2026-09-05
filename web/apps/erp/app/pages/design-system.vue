<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  WegoAlert,
  WegoBadge,
  WegoButton,
  WegoCheckbox,
  WegoDialog,
  WegoEmptyState,
  WegoInput,
  WegoPageHeader,
  WegoPagination,
  WegoPanel,
  WegoSelect,
  WegoTextarea,
} from "@wego/ui";
import { type AuthSession, readAuthSession } from "../composables/useAuthSession";
import { type ThemePreference, useTheme } from "../composables/useTheme";

useHead({
  title: "Design System · Wego Platform",
  meta: [{ name: "robots", content: "noindex,nofollow" }],
});

const session = ref<AuthSession | null>(null);
onMounted(() => {
  session.value = readAuthSession();
});

const { preference, setPreference } = useTheme();
const themeOptions: ThemePreference[] = ["system", "light", "dark"];

const inputValue = ref("");
const invalidValue = ref("");
const page = ref(0);

const dialogOpen = ref(false);
const dialogResult = ref("");
function confirmDialog() {
  dialogResult.value = "Confirmed.";
  dialogOpen.value = false;
}
function cancelDialog() {
  dialogResult.value = "Cancelled.";
  dialogOpen.value = false;
}
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div v-if="!session" class="mx-auto max-w-4xl">
      <p class="rounded-wego-card border border-wego-border bg-wego-surface p-6 text-wego-muted">
        You need to sign in to view the design system.
      </p>
    </div>
    <div v-else class="mx-auto max-w-4xl space-y-10">
      <WegoPageHeader
        title="Design System"
        description="Every shared component (web/packages/ui) and platform token (web/packages/design-tokens), in one place — WEGO-014's own reference, not a business page."
      />

      <WegoPanel title="Theme">
        <div class="flex gap-3">
          <WegoButton
            v-for="option in themeOptions"
            :key="option"
            type="button"
            :variant="preference === option ? 'primary' : 'secondary'"
            @click="setPreference(option)"
          >
            {{ option }}
          </WegoButton>
        </div>
        <p class="mt-3 text-sm text-wego-muted">
          Current preference: <strong>{{ preference }}</strong> — persisted, and the same mechanism the
          navigation shell's own theme control calls.
        </p>
      </WegoPanel>

      <WegoPanel title="Buttons">
        <div class="flex flex-wrap items-center gap-3">
          <WegoButton variant="primary">Primary</WegoButton>
          <WegoButton variant="secondary">Secondary</WegoButton>
          <WegoButton variant="destructive">Destructive</WegoButton>
          <WegoButton variant="ghost">Ghost</WegoButton>
          <WegoButton variant="primary" loading>Saving</WegoButton>
          <WegoButton variant="primary" disabled>Disabled</WegoButton>
        </div>
      </WegoPanel>

      <WegoPanel title="Alerts">
        <div class="space-y-2">
          <WegoAlert variant="success">Offering created.</WegoAlert>
          <WegoAlert variant="warning">This offering has no capacity limit.</WegoAlert>
          <WegoAlert variant="danger">Request failed (validation_failed).</WegoAlert>
          <WegoAlert variant="info">Reports are read-only.</WegoAlert>
        </div>
      </WegoPanel>

      <WegoPanel title="Badges">
        <div class="flex flex-wrap gap-2">
          <WegoBadge tone="neutral">DRAFT</WegoBadge>
          <WegoBadge tone="accent">READY</WegoBadge>
          <WegoBadge tone="success">ACTIVE</WegoBadge>
          <WegoBadge tone="warning">PENDING</WegoBadge>
          <WegoBadge tone="danger">CANCELLED</WegoBadge>
          <WegoBadge tone="info">POSTED</WegoBadge>
        </div>
      </WegoPanel>

      <WegoPanel title="Form controls">
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="ds-text" v-model="inputValue" label="Title" placeholder="E2E Lifecycle Trip" />
          <WegoInput id="ds-disabled" label="Code (locked while editing)" model-value="9910" disabled />
          <WegoInput
            id="ds-invalid"
            v-model="invalidValue"
            label="Email"
            error="Required"
            help="This won't show while there's an error."
          />
          <WegoSelect id="ds-select" label="Offering type" model-value="DIVE_TRIP">
            <option value="DIVE_TRIP">Dive trip</option>
            <option value="COURSE">Course</option>
          </WegoSelect>
          <WegoTextarea id="ds-textarea" label="Notes" model-value="" />
          <div class="flex items-end">
            <WegoCheckbox id="ds-checkbox" model-value>accounting:journal-view</WegoCheckbox>
          </div>
        </div>
      </WegoPanel>

      <WegoPanel title="Pagination">
        <WegoPagination :page="page" :has-next-page="page < 2" @previous="page = Math.max(0, page - 1)" @next="page++" />
      </WegoPanel>

      <WegoPanel title="Empty state">
        <WegoEmptyState message="Nothing scheduled in the next 7 days." />
      </WegoPanel>

      <WegoPanel title="Dialog">
        <WegoButton type="button" variant="destructive" @click="dialogOpen = true">Close offering…</WegoButton>
        <p v-if="dialogResult" class="mt-3 text-sm text-wego-muted">Last result: {{ dialogResult }}</p>
        <WegoDialog :open="dialogOpen" title="Close this offering?" @close="dialogOpen = false">
          <p class="text-sm text-wego-muted">This offering will stop accepting new bookings. This cannot be undone.</p>
          <template #actions>
            <WegoButton type="button" variant="secondary" @click="cancelDialog">Cancel</WegoButton>
            <WegoButton type="button" variant="destructive" @click="confirmDialog">Close offering</WegoButton>
          </template>
        </WegoDialog>
      </WegoPanel>
    </div>
  </main>
</template>
