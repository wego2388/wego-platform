<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput, WegoPageHeader, WegoPanel, WegoSelect } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  type Account,
  AccountingApiError,
  type JournalEntry,
  type JournalLineDirection,
  listAccounts,
  listJournalEntries,
  postJournalEntry,
  reverseJournalEntry,
} from "../composables/useAccountingApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Journal Entries · Wego Platform" });

const session = ref<AuthSession | null>(null);
const accounts = ref<Account[]>([]);
const entries = ref<JournalEntry[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const accountFilter = ref("");

const canManage = () => hasPermission(session.value, "accounting:journal-manage");
const canView = () => hasPermission(session.value, "accounting:journal-view");

function accountLabel(accountId: string): string {
  const account = accounts.value.find((existing) => existing.id === accountId);
  return account ? `${account.code} · ${account.name}` : accountId;
}

function handleApiError(error: unknown) {
  if (error instanceof AccountingApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof AccountingApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "too_few_lines") return "A journal entry needs at least 2 lines.";
    if (error.errorCode === "missing_debit_or_credit") return "A journal entry needs at least one debit and one credit line.";
    if (error.errorCode === "unbalanced") return "Debits and credits don't balance.";
    if (error.errorCode === "account_not_found") return "One of the selected accounts could not be found.";
    if (error.errorCode === "account_inactive") return "One of the selected accounts is inactive.";
    if (error.errorCode === "already_reversed") return "This entry has already been reversed.";
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadAll() {
  if (!session.value) return;
  if (!canView()) {
    entries.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const [loadedAccounts, loadedEntries] = await Promise.all([
      listAccounts(session.value.token, { activeOnly: false }),
      listJournalEntries(session.value.token, { accountId: accountFilter.value || undefined }),
    ]);
    accounts.value = loadedAccounts;
    entries.value = loadedEntries;
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function runFilter() {
  loadAll();
}

function blankLine() {
  return { accountId: "", direction: "DEBIT" as JournalLineDirection, amount: "" };
}

function blankForm() {
  return {
    entryDate: "",
    description: "",
    reference: "",
    currencyCode: "EGP",
    lines: [blankLine(), blankLine()],
  };
}

const form = ref(blankForm());
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

function addLine() {
  form.value.lines = [...form.value.lines, blankLine()];
}

function removeLine(index: number) {
  if (form.value.lines.length <= 2) return;
  form.value.lines = form.value.lines.filter((_, i) => i !== index);
}

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    await postJournalEntry(session.value.token, {
      entryDate: form.value.entryDate,
      description: form.value.description,
      reference: form.value.reference || undefined,
      currencyCode: form.value.currencyCode,
      lines: form.value.lines.map((line) => ({ accountId: line.accountId, direction: line.direction, amount: line.amount })),
    });
    form.value = blankForm();
    formState.value = "idle";
    await loadAll();
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

const rowState = ref<Record<string, "idle" | "submitting" | "error">>({});
const rowError = ref<Record<string, string>>({});
const reverseReason = ref<Record<string, string>>({});

function alreadyReversed(entry: JournalEntry): boolean {
  return entries.value.some((existing) => existing.reversalOfEntryId === entry.id);
}

async function submitReverse(entry: JournalEntry) {
  if (!session.value) return;
  const reason = reverseReason.value[entry.id];
  if (!reason) return;
  rowState.value[entry.id] = "submitting";
  rowError.value[entry.id] = "";
  try {
    await reverseJournalEntry(session.value.token, entry.id, reason);
    rowState.value[entry.id] = "idle";
    await loadAll();
  } catch (error) {
    handleApiError(error);
    rowState.value[entry.id] = "error";
    rowError.value[entry.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadAll();
});
</script>

<template>
  <WegoPageHeader title="Journal Entries" description="Every posted debit and credit, and its reversal history." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view journal entries.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel title="Entries" class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to view journal entries (accounting:journal-view).
      </p>
      <template v-else>
        <div class="flex flex-wrap items-end gap-3">
          <WegoSelect id="accountFilter" v-model="accountFilter" label="Account" @change="runFilter">
            <option value="">All accounts</option>
            <option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.code }} · {{ account.name }}</option>
          </WegoSelect>
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && entries.length === 0" class="mt-3 text-sm text-wego-muted">No journal entries yet.</p>
        <!--
          This entry's own <li> and the two exact text runs below (the
          debit/credit summary paragraph and each line's "DIRECTION amount
          — account" text) are frozen on purpose: the real E2E suite
          locates each entry with locator("li", { hasText: DESCRIPTION })
          and asserts these exact substrings inside it
          ("500.00 EGP debit / 500.00 EGP credit", "DEBIT 15000.00"). Only
          the surrounding chrome (WegoPanel/WegoSelect/WegoInput) changed.
        -->
        <ul v-else class="mt-4 space-y-3">
          <li v-for="entry in entries" :key="entry.id" class="rounded-wego-control border border-wego-border p-4">
            <p class="font-semibold">
              {{ entry.entryDate }} · {{ entry.description }}<span v-if="entry.reference"> · {{ entry.reference }}</span>
            </p>
            <p class="mt-1 text-sm text-wego-muted">
              {{ entry.debitTotal }} {{ entry.currencyCode }} debit / {{ entry.creditTotal }} {{ entry.currencyCode }} credit
              <span v-if="entry.reversalOfEntryId"> · reverses another entry</span>
            </p>
            <ul class="mt-2 space-y-1">
              <li v-for="line in entry.lines" :key="line.id" class="text-sm text-wego-muted">
                {{ line.direction }} {{ line.amount }} — {{ accountLabel(line.accountId) }}
              </li>
            </ul>
            <WegoAlert v-if="rowState[entry.id] === 'error'" variant="danger" class="mt-2">{{ rowError[entry.id] }}</WegoAlert>

            <div
              v-if="canManage() && !entry.reversalOfEntryId && !alreadyReversed(entry)"
              class="mt-3 flex flex-wrap items-end gap-2"
            >
              <WegoInput
                :id="`reverse-reason-${entry.id}`"
                :model-value="reverseReason[entry.id] ?? ''"
                label="Reversal reason"
                class="min-w-0 flex-1"
                @update:model-value="(value) => (reverseReason[entry.id] = value)"
              />
              <WegoButton
                type="button"
                variant="secondary"
                :disabled="rowState[entry.id] === 'submitting' || !reverseReason[entry.id]"
                @click="submitReverse(entry)"
              >
                Reverse
              </WegoButton>
            </div>
          </li>
        </ul>
      </template>
    </WegoPanel>

    <WegoPanel v-if="canManage()" title="Post a journal entry" class="mt-8">
      <form class="space-y-5" @submit.prevent="submitForm">
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="entryDate" v-model="form.entryDate" label="Date" type="date" required />
          <WegoInput id="currencyCode" v-model="form.currencyCode" label="Currency" required />
        </div>
        <WegoInput id="description" v-model="form.description" label="Description" required />
        <WegoInput id="reference" v-model="form.reference" label="Reference (optional)" />

        <div>
          <div class="flex items-center justify-between">
            <span class="block text-sm font-medium text-wego-muted">Lines (must balance)</span>
            <WegoButton type="button" variant="secondary" @click="addLine">Add line</WegoButton>
          </div>
          <div v-for="(line, index) in form.lines" :key="index" class="mt-3 grid gap-3 sm:grid-cols-4">
            <WegoSelect :id="`line-account-${index}`" v-model="line.accountId" label="Account" required>
              <option value="" disabled>Select an account…</option>
              <option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.code }} · {{ account.name }}</option>
            </WegoSelect>
            <WegoSelect :id="`line-direction-${index}`" v-model="line.direction" label="Direction">
              <option value="DEBIT">DEBIT</option>
              <option value="CREDIT">CREDIT</option>
            </WegoSelect>
            <WegoInput :id="`line-amount-${index}`" v-model="line.amount" label="Amount" placeholder="0.00" />
            <WegoButton type="button" variant="secondary" :disabled="form.lines.length <= 2" @click="removeLine(index)">
              Remove
            </WegoButton>
          </div>
        </div>

        <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

        <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
          Post entry
        </WegoButton>
      </form>
    </WegoPanel>
  </template>
</template>
