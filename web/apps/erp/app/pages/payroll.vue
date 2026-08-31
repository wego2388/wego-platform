<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import { type EmployeeSummary, listEmployees } from "../composables/useHrApi";
import {
  createPayrollRun,
  discardPayrollRun,
  type PayrollRun,
  PayrollApiError,
  postPayrollRun,
  listPayrollRuns,
  type PayrollRunStatus,
} from "../composables/usePayrollApi";

useHead({ title: "Payroll · Wego Platform" });

const session = ref<AuthSession | null>(null);
const employees = ref<EmployeeSummary[]>([]);
const runs = ref<PayrollRun[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const statusFilter = ref<PayrollRunStatus | "">("");

const canManage = () => hasPermission(session.value, "payroll:manage");
const canView = () => hasPermission(session.value, "payroll:view");

function employeeName(employeeId: string): string {
  return employees.value.find((employee) => employee.id === employeeId)?.fullName ?? employeeId;
}

function handleApiError(error: unknown) {
  if (error instanceof PayrollApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof PayrollApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "no_eligible_employees") return "No active employee currently has a base salary set.";
    if (error.errorCode === "mixed_currencies") {
      return "Active salaried employees use more than one currency — run payroll separately per currency.";
    }
    if (error.errorCode === "overlaps_existing_run") return "This pay period overlaps an existing payroll run.";
    if (error.errorCode === "not_draft") return "This run is no longer a draft.";
    if (error.errorCode.startsWith("salaries_expense_account")) return "The Salaries Expense account is missing or inactive.";
    if (error.errorCode.startsWith("wages_payable_account")) return "The Wages Payable account is missing or inactive.";
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadAll() {
  if (!session.value) return;
  if (!canView()) {
    runs.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const [loadedEmployees, loadedRuns] = await Promise.all([
      listEmployees(session.value.token, { status: "ACTIVE" }),
      listPayrollRuns(session.value.token, { status: statusFilter.value || undefined }),
    ]);
    employees.value = loadedEmployees;
    runs.value = loadedRuns;
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

function blankForm() {
  return { payPeriodStart: "", payPeriodEnd: "" };
}

const form = ref(blankForm());
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    await createPayrollRun(session.value.token, { payPeriodStart: form.value.payPeriodStart, payPeriodEnd: form.value.payPeriodEnd });
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

async function submitPost(run: PayrollRun) {
  if (!session.value) return;
  rowState.value[run.id] = "submitting";
  rowError.value[run.id] = "";
  try {
    await postPayrollRun(session.value.token, run.id);
    rowState.value[run.id] = "idle";
    await loadAll();
  } catch (error) {
    handleApiError(error);
    rowState.value[run.id] = "error";
    rowError.value[run.id] = errorText(error);
  }
}

async function submitDiscard(run: PayrollRun) {
  if (!session.value) return;
  if (!window.confirm(`Discard the draft payroll run for ${run.payPeriodStart} – ${run.payPeriodEnd}?`)) return;
  rowState.value[run.id] = "submitting";
  rowError.value[run.id] = "";
  try {
    await discardPayrollRun(session.value.token, run.id);
    rowState.value[run.id] = "idle";
    await loadAll();
  } catch (error) {
    handleApiError(error);
    rowState.value[run.id] = "error";
    rowError.value[run.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadAll();
});
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Wego Platform</p>
      <h1 class="mt-4 text-3xl font-semibold tracking-tight">Payroll</h1>

      <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
        <p>You need to sign in to view payroll runs.</p>
        <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
      </div>

      <template v-else>
        <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

        <section class="mt-8">
          <h2 class="text-xl font-semibold">Payroll runs</h2>
          <p v-if="!canView()" class="mt-3 text-sm text-wego-muted">
            Your account doesn't have permission to view payroll runs (payroll:view).
          </p>
          <template v-else>
            <div class="mt-4 flex flex-wrap items-end gap-3">
              <div>
                <label for="statusFilter" class="block text-sm font-medium text-wego-muted">Status</label>
                <select
                  id="statusFilter"
                  v-model="statusFilter"
                  class="mt-2 rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
                  @change="runFilter"
                >
                  <option value="">All</option>
                  <option value="DRAFT">Draft</option>
                  <option value="POSTED">Posted</option>
                </select>
              </div>
            </div>

            <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
            <p v-else-if="listState === 'loaded' && runs.length === 0" class="mt-3 text-sm text-wego-muted">No payroll runs yet.</p>
            <ul v-else class="mt-4 space-y-3">
              <li v-for="run in runs" :key="run.id" class="rounded-wego-card border border-wego-border bg-wego-surface p-4">
                <p class="font-semibold">{{ run.payPeriodStart }} – {{ run.payPeriodEnd }} · {{ run.status }}</p>
                <p class="mt-1 text-sm text-wego-muted">{{ run.totalAmount }} {{ run.currencyCode }} across {{ run.lines.length }} employees</p>
                <ul class="mt-2 space-y-1">
                  <li v-for="line in run.lines" :key="line.employeeId" class="text-sm text-wego-muted">
                    {{ employeeName(line.employeeId) }} — {{ line.amount }} {{ run.currencyCode }}
                  </li>
                </ul>
                <WegoAlert v-if="rowState[run.id] === 'error'" variant="danger" class="mt-2">{{ rowError[run.id] }}</WegoAlert>

                <div v-if="canManage() && run.status === 'DRAFT'" class="mt-3 flex gap-2">
                  <WegoButton type="button" :disabled="rowState[run.id] === 'submitting'" @click="submitPost(run)">Post</WegoButton>
                  <WegoButton
                    type="button"
                    variant="secondary"
                    :disabled="rowState[run.id] === 'submitting'"
                    @click="submitDiscard(run)"
                  >
                    Discard
                  </WegoButton>
                </div>
              </li>
            </ul>
          </template>
        </section>

        <section v-if="canManage()" class="mt-10 rounded-wego-card border border-wego-border bg-wego-surface p-6">
          <h2 class="text-xl font-semibold">New payroll run</h2>
          <p class="mt-2 text-sm text-wego-muted">
            Includes every currently active employee with a base salary set. Nothing is posted to the ledger until you Post it.
          </p>
          <form class="mt-6 space-y-5" @submit.prevent="submitForm">
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="payPeriodStart" v-model="form.payPeriodStart" label="Pay period start" type="date" required />
              <WegoInput id="payPeriodEnd" v-model="form.payPeriodEnd" label="Pay period end" type="date" required />
            </div>

            <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

            <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
              Create draft
            </WegoButton>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
