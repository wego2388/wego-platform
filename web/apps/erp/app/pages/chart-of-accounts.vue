<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoCheckbox, WegoInput, WegoPageHeader, WegoPanel, WegoSelect } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  type Account,
  AccountingApiError,
  type AccountType,
  createAccount,
  deactivateAccount,
  listAccounts,
  reactivateAccount,
  updateAccount,
} from "../composables/useAccountingApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Chart of Accounts · Wego Platform" });

const session = ref<AuthSession | null>(null);
const accounts = ref<Account[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const typeFilter = ref<AccountType | "">("");
const showInactive = ref(false);

const canManage = () => hasPermission(session.value, "accounting:coa-manage");
const canView = () => hasPermission(session.value, "accounting:coa-view");

const accountTypes: AccountType[] = ["ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"];

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
    if (error.errorCode === "code_already_in_use") return "That account code is already in use.";
    if (error.errorCode === "parent_account_not_found") return "The parent account you selected could not be found.";
    if (error.errorCode === "already_inactive") return "That account is already inactive.";
    if (error.errorCode === "already_active") return "That account is already active.";
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadAccounts() {
  if (!session.value) return;
  if (!canView()) {
    accounts.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    accounts.value = await listAccounts(session.value.token, {
      accountType: typeFilter.value || undefined,
      activeOnly: !showInactive.value,
    });
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function runFilter() {
  loadAccounts();
}

function blankForm() {
  return { code: "", name: "", accountType: "ASSET" as AccountType, description: "" };
}

const form = ref(blankForm());
const editingAccountId = ref<string | null>(null);
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

function startEdit(account: Account) {
  editingAccountId.value = account.id;
  form.value = { code: account.code, name: account.name, accountType: account.accountType, description: account.description ?? "" };
  formState.value = "idle";
  formError.value = "";
}

function cancelEdit() {
  editingAccountId.value = null;
  form.value = blankForm();
  formState.value = "idle";
  formError.value = "";
}

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    if (editingAccountId.value) {
      await updateAccount(session.value.token, editingAccountId.value, {
        name: form.value.name,
        description: form.value.description || undefined,
      });
    } else {
      await createAccount(session.value.token, {
        code: form.value.code,
        name: form.value.name,
        accountType: form.value.accountType,
        description: form.value.description || undefined,
      });
    }
    cancelEdit();
    await loadAccounts();
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

const rowState = ref<Record<string, "idle" | "submitting" | "error">>({});
const rowError = ref<Record<string, string>>({});

async function toggleActive(account: Account) {
  if (!session.value) return;
  rowState.value[account.id] = "submitting";
  rowError.value[account.id] = "";
  try {
    if (account.active) {
      await deactivateAccount(session.value.token, account.id);
    } else {
      await reactivateAccount(session.value.token, account.id);
    }
    rowState.value[account.id] = "idle";
    await loadAccounts();
  } catch (error) {
    handleApiError(error);
    rowState.value[account.id] = "error";
    rowError.value[account.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadAccounts();
});
</script>

<template>
  <WegoPageHeader title="Chart of Accounts" description="The ledger accounts everything else in accounting posts against." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view the chart of accounts.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel title="Accounts" class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to view the chart of accounts (accounting:coa-view).
      </p>
      <template v-else>
        <div class="flex flex-wrap items-end gap-4">
          <WegoSelect id="typeFilter" v-model="typeFilter" label="Type" @change="runFilter">
            <option value="">All types</option>
            <option v-for="type in accountTypes" :key="type" :value="type">{{ type }}</option>
          </WegoSelect>
          <WegoCheckbox id="showInactive" v-model="showInactive" @change="runFilter">Show inactive</WegoCheckbox>
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && accounts.length === 0" class="mt-3 text-sm text-wego-muted">No accounts yet.</p>
        <ul v-else class="mt-4 space-y-3">
          <li v-for="account in accounts" :key="account.id" class="rounded-wego-control border border-wego-border p-4">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <p class="font-semibold">{{ account.code }} · {{ account.name }}</p>
                  <WegoBadge :tone="account.active ? 'success' : 'neutral'">{{ account.active ? "ACTIVE" : "INACTIVE" }}</WegoBadge>
                </div>
                <p class="mt-1 text-sm text-wego-muted">{{ account.accountType }} · normal balance {{ account.normalBalance }}</p>
                <p v-if="account.description" class="mt-1 text-sm text-wego-muted">{{ account.description }}</p>
              </div>
              <div v-if="canManage()" class="flex shrink-0 gap-2">
                <WegoButton type="button" variant="secondary" @click="startEdit(account)">Edit</WegoButton>
                <WegoButton
                  type="button"
                  variant="secondary"
                  :disabled="rowState[account.id] === 'submitting'"
                  @click="toggleActive(account)"
                >
                  {{ account.active ? "Deactivate" : "Reactivate" }}
                </WegoButton>
              </div>
            </div>
            <WegoAlert v-if="rowState[account.id] === 'error'" variant="danger" class="mt-2">{{ rowError[account.id] }}</WegoAlert>
          </li>
        </ul>
      </template>
    </WegoPanel>

    <WegoPanel v-if="canManage()" :title="editingAccountId ? 'Edit account' : 'New account'" class="mt-8">
      <form class="space-y-5" @submit.prevent="submitForm">
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="code" v-model="form.code" label="Code" required :disabled="!!editingAccountId" />
          <WegoSelect id="accountType" v-model="form.accountType" label="Type" :disabled="!!editingAccountId">
            <option v-for="type in accountTypes" :key="type" :value="type">{{ type }}</option>
          </WegoSelect>
        </div>
        <WegoInput id="name" v-model="form.name" label="Name" required />
        <WegoInput id="description" v-model="form.description" label="Description (optional)" />

        <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

        <div class="flex gap-3">
          <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
            {{ editingAccountId ? "Save changes" : "Create account" }}
          </WegoButton>
          <WegoButton v-if="editingAccountId" type="button" variant="secondary" @click="cancelEdit">Cancel</WegoButton>
        </div>
      </form>
    </WegoPanel>
  </template>
</template>
