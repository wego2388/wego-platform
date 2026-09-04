<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput, WegoPageHeader, WegoPanel } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  AccountingApiError,
  type BalanceSheet,
  getBalanceSheet,
  getIncomeStatement,
  getTrialBalance,
  type IncomeStatement,
  type TrialBalance,
} from "../composables/useAccountingApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Financial Reports · Wego Platform" });

const session = ref<AuthSession | null>(null);
const canView = () => hasPermission(session.value, "accounting:journal-view");

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
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

const trialBalanceDate = ref("");
const trialBalance = ref<TrialBalance | null>(null);
const trialBalanceState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const trialBalanceError = ref("");

async function runTrialBalance() {
  if (!session.value || !trialBalanceDate.value) return;
  trialBalanceState.value = "loading";
  trialBalanceError.value = "";
  try {
    trialBalance.value = await getTrialBalance(session.value.token, trialBalanceDate.value);
    trialBalanceState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    trialBalanceState.value = "error";
    trialBalanceError.value = errorText(error);
  }
}

const incomeFrom = ref("");
const incomeTo = ref("");
const incomeStatement = ref<IncomeStatement | null>(null);
const incomeState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const incomeError = ref("");

async function runIncomeStatement() {
  if (!session.value || !incomeFrom.value || !incomeTo.value) return;
  incomeState.value = "loading";
  incomeError.value = "";
  try {
    incomeStatement.value = await getIncomeStatement(session.value.token, incomeFrom.value, incomeTo.value);
    incomeState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    incomeState.value = "error";
    incomeError.value = errorText(error);
  }
}

const balanceSheetDate = ref("");
const balanceSheet = ref<BalanceSheet | null>(null);
const balanceSheetState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const balanceSheetError = ref("");

async function runBalanceSheet() {
  if (!session.value || !balanceSheetDate.value) return;
  balanceSheetState.value = "loading";
  balanceSheetError.value = "";
  try {
    balanceSheet.value = await getBalanceSheet(session.value.token, balanceSheetDate.value);
    balanceSheetState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    balanceSheetState.value = "error";
    balanceSheetError.value = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
});
</script>

<template>
  <WegoPageHeader title="Financial Reports" description="Trial balance, income statement, and balance sheet, run on demand." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view financial reports.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <p v-else-if="!canView()" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6 text-sm text-wego-muted">
    Your account doesn't have permission to view financial reports (accounting:journal-view).
  </p>

  <template v-else>
    <!--
      Each "Run" button now has a distinguishing accessible name (Run
      trial balance / Run income statement / Run balance sheet) instead
      of three identical "Run" buttons the old E2E suite selected by DOM
      position (runButtons.nth(0/1/2)) — the exact fix UX_REDESIGN_CONTRACT.md
      flagged for this phase. The E2E spec and this page's own Vitest spec
      are updated in the same change to select by name, not position.
    -->
    <WegoPanel title="Trial Balance" description="Every account's balance as of a date — total debits should equal total credits." class="mt-8">
      <div class="flex flex-wrap items-end gap-3">
        <WegoInput id="trialBalanceDate" v-model="trialBalanceDate" label="As of date" type="date" class="min-w-0 flex-1" />
        <WegoButton type="button" :disabled="!trialBalanceDate" @click="runTrialBalance">Run trial balance</WegoButton>
      </div>
      <WegoAlert v-if="trialBalanceState === 'error'" variant="danger" class="mt-3">{{ trialBalanceError }}</WegoAlert>
      <div v-if="trialBalance" class="mt-4">
        <ul class="space-y-1">
          <li
            v-for="line in trialBalance.lines"
            :key="line.accountId"
            class="flex justify-between text-sm text-wego-muted"
          >
            <span>{{ line.code }} · {{ line.name }}</span>
            <span>{{ line.debitBalance !== "0.00" ? `${line.debitBalance} DR` : `${line.creditBalance} CR` }}</span>
          </li>
        </ul>
        <p class="mt-3 border-t border-wego-border pt-3 text-sm font-semibold">
          Total debits {{ trialBalance.totalDebits }} · Total credits {{ trialBalance.totalCredits }}
        </p>
      </div>
    </WegoPanel>

    <WegoPanel title="Income Statement" description="Revenue minus expenses over a period." class="mt-8">
      <div class="flex flex-wrap items-end gap-3">
        <WegoInput id="incomeFrom" v-model="incomeFrom" label="From" type="date" class="min-w-0 flex-1" />
        <WegoInput id="incomeTo" v-model="incomeTo" label="To" type="date" class="min-w-0 flex-1" />
        <WegoButton type="button" :disabled="!incomeFrom || !incomeTo" @click="runIncomeStatement">Run income statement</WegoButton>
      </div>
      <WegoAlert v-if="incomeState === 'error'" variant="danger" class="mt-3">{{ incomeError }}</WegoAlert>
      <div v-if="incomeStatement" class="mt-4 space-y-4">
        <div>
          <p class="text-sm font-semibold">Revenue</p>
          <ul class="mt-1 space-y-1">
            <li v-for="line in incomeStatement.revenueLines" :key="line.accountId" class="flex justify-between text-sm text-wego-muted">
              <span>{{ line.code }} · {{ line.name }}</span>
              <span>{{ line.amount }}</span>
            </li>
          </ul>
        </div>
        <div>
          <p class="text-sm font-semibold">Expenses</p>
          <ul class="mt-1 space-y-1">
            <li v-for="line in incomeStatement.expenseLines" :key="line.accountId" class="flex justify-between text-sm text-wego-muted">
              <span>{{ line.code }} · {{ line.name }}</span>
              <span>{{ line.amount }}</span>
            </li>
          </ul>
        </div>
        <p class="border-t border-wego-border pt-3 text-sm font-semibold">
          Total revenue {{ incomeStatement.totalRevenue }} · Total expenses {{ incomeStatement.totalExpenses }} · Net income
          {{ incomeStatement.netIncome }}
        </p>
      </div>
    </WegoPanel>

    <WegoPanel
      title="Balance Sheet"
      description="Assets, liabilities, and equity as of a date — assets should equal liabilities plus equity."
      class="mt-8"
    >
      <div class="flex flex-wrap items-end gap-3">
        <WegoInput id="balanceSheetDate" v-model="balanceSheetDate" label="As of date" type="date" class="min-w-0 flex-1" />
        <WegoButton type="button" :disabled="!balanceSheetDate" @click="runBalanceSheet">Run balance sheet</WegoButton>
      </div>
      <WegoAlert v-if="balanceSheetState === 'error'" variant="danger" class="mt-3">{{ balanceSheetError }}</WegoAlert>
      <div v-if="balanceSheet" class="mt-4 space-y-4">
        <div>
          <p class="text-sm font-semibold">Assets</p>
          <ul class="mt-1 space-y-1">
            <li v-for="line in balanceSheet.assetLines" :key="line.accountId" class="flex justify-between text-sm text-wego-muted">
              <span>{{ line.code }} · {{ line.name }}</span>
              <span>{{ line.amount }}</span>
            </li>
          </ul>
          <p class="mt-1 text-sm font-semibold">Total assets {{ balanceSheet.totalAssets }}</p>
        </div>
        <div>
          <p class="text-sm font-semibold">Liabilities</p>
          <ul class="mt-1 space-y-1">
            <li v-for="line in balanceSheet.liabilityLines" :key="line.accountId" class="flex justify-between text-sm text-wego-muted">
              <span>{{ line.code }} · {{ line.name }}</span>
              <span>{{ line.amount }}</span>
            </li>
          </ul>
          <p class="mt-1 text-sm font-semibold">Total liabilities {{ balanceSheet.totalLiabilities }}</p>
        </div>
        <div>
          <p class="text-sm font-semibold">Equity</p>
          <ul class="mt-1 space-y-1">
            <li
              v-for="(line, index) in balanceSheet.equityLines"
              :key="line.accountId ?? `synthetic-${index}`"
              class="flex justify-between text-sm text-wego-muted"
            >
              <span>{{ line.code ? `${line.code} · ${line.name}` : line.name }}</span>
              <span>{{ line.amount }}</span>
            </li>
          </ul>
          <p class="mt-1 text-sm font-semibold">Total equity {{ balanceSheet.totalEquity }}</p>
        </div>
      </div>
    </WegoPanel>
  </template>
</template>
