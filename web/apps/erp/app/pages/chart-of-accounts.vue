<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
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
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Wego Platform</p>
      <h1 class="mt-4 text-3xl font-semibold tracking-tight">Chart of Accounts</h1>

      <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
        <p>You need to sign in to view the chart of accounts.</p>
        <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
      </div>

      <template v-else>
        <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

        <section class="mt-8">
          <h2 class="text-xl font-semibold">Accounts</h2>
          <p v-if="!canView()" class="mt-3 text-sm text-wego-muted">
            Your account doesn't have permission to view the chart of accounts (accounting:coa-view).
          </p>
          <template v-else>
            <div class="mt-4 flex flex-wrap items-end gap-3">
              <div>
                <label for="typeFilter" class="block text-sm font-medium text-wego-muted">Type</label>
                <select
                  id="typeFilter"
                  v-model="typeFilter"
                  class="mt-2 rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
                  @change="runFilter"
                >
                  <option value="">All types</option>
                  <option v-for="type in accountTypes" :key="type" :value="type">{{ type }}</option>
                </select>
              </div>
              <label class="flex items-center gap-2 text-sm">
                <input v-model="showInactive" type="checkbox" @change="runFilter" >
                Show inactive
              </label>
            </div>

            <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
            <p v-else-if="listState === 'loaded' && accounts.length === 0" class="mt-3 text-sm text-wego-muted">No accounts yet.</p>
            <ul v-else class="mt-4 space-y-3">
              <li v-for="account in accounts" :key="account.id" class="rounded-wego-card border border-wego-border bg-wego-surface p-4">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p class="font-semibold">{{ account.code }} · {{ account.name }}</p>
                    <p class="mt-1 text-sm text-wego-muted">
                      {{ account.accountType }} · normal balance {{ account.normalBalance }} ·
                      {{ account.active ? "ACTIVE" : "INACTIVE" }}
                    </p>
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
        </section>

        <section v-if="canManage()" class="mt-10 rounded-wego-card border border-wego-border bg-wego-surface p-6">
          <h2 class="text-xl font-semibold">{{ editingAccountId ? "Edit account" : "New account" }}</h2>
          <form class="mt-6 space-y-5" @submit.prevent="submitForm">
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="code" v-model="form.code" label="Code" required :disabled="!!editingAccountId" />
              <div>
                <label for="accountType" class="block text-sm font-medium text-wego-muted">Type</label>
                <select
                  id="accountType"
                  v-model="form.accountType"
                  :disabled="!!editingAccountId"
                  class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
                >
                  <option v-for="type in accountTypes" :key="type" :value="type">{{ type }}</option>
                </select>
              </div>
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
        </section>
      </template>
    </div>
  </main>
</template>
