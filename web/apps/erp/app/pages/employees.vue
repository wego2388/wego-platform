<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoInput, WegoPageHeader, WegoPanel, WegoSelect } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  createEmployee,
  type EmployeeStatus,
  type EmployeeSummary,
  getEmployee,
  HrApiError,
  listEmployees,
  HR_PAGE_SIZE,
  terminateEmployee,
  updateEmployee,
  type UpsertEmployeeBody,
} from "../composables/useHrApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Employees · Wego Platform" });

const session = ref<AuthSession | null>(null);
const employees = ref<EmployeeSummary[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const page = ref(0);
const hasNextPage = ref(false);
const statusFilter = ref<EmployeeStatus | "">("ACTIVE");
const search = ref("");

const canManage = () => hasPermission(session.value, "hr:employee-manage");
const canView = () => hasPermission(session.value, "hr:employee-view");

function blankForm() {
  return {
    fullName: "",
    position: "",
    department: "",
    hireDate: "",
    email: "",
    phone: "",
    amount: "",
    currencyCode: "EUR",
    linkedUserId: "",
  };
}

const form = ref(blankForm());
// null while creating a new record; the employee's id while editing an existing one.
const editingEmployeeId = ref<string | null>(null);
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

const terminateState = ref<Record<string, "idle" | "submitting" | "error">>({});
const terminateError = ref<Record<string, string>>({});
const terminateReason = ref<Record<string, string>>({});

function handleApiError(error: unknown) {
  if (error instanceof HrApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof HrApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "already_terminated") return "That employee is already terminated.";
    if (error.errorCode === "linked_user_not_active_staff") return "The linked staff account must be an active user.";
    if (error.status === 404) return "Not found.";
    if (error.status === 400) return "Check the form — one of the fields isn't valid.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadEmployees() {
  if (!session.value) return;
  if (!canView()) {
    employees.value = [];
    hasNextPage.value = false;
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const result = await listEmployees(session.value.token, {
      status: statusFilter.value || undefined,
      search: search.value || undefined,
      page: page.value,
    });
    employees.value = result;
    hasNextPage.value = result.length === HR_PAGE_SIZE;
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function nextPage() {
  page.value += 1;
  loadEmployees();
}

function previousPage() {
  if (page.value === 0) return;
  page.value -= 1;
  loadEmployees();
}

function runSearch() {
  page.value = 0;
  loadEmployees();
}

async function startEdit(summary: EmployeeSummary) {
  if (!session.value) return;
  // The list only carries the roster projection (no salary/email/phone) —
  // fetch the full record to actually populate the edit form.
  const employee = await getEmployee(session.value.token, summary.id);
  editingEmployeeId.value = employee.id;
  form.value = {
    fullName: employee.fullName,
    position: employee.position,
    department: employee.department ?? "",
    hireDate: employee.hireDate,
    email: employee.email ?? "",
    phone: employee.phone ?? "",
    amount: employee.baseSalary?.amount ?? "",
    currencyCode: employee.baseSalary?.currencyCode ?? "EUR",
    linkedUserId: employee.linkedUserId ?? "",
  };
  formState.value = "idle";
  formError.value = "";
  const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  window.scrollTo({ top: document.body.scrollHeight, behavior: prefersReducedMotion ? "auto" : "smooth" });
}

function cancelEdit() {
  editingEmployeeId.value = null;
  form.value = blankForm();
  formState.value = "idle";
  formError.value = "";
}

function buildRequestBody(): UpsertEmployeeBody {
  return {
    fullName: form.value.fullName,
    position: form.value.position,
    department: form.value.department || undefined,
    hireDate: form.value.hireDate,
    email: form.value.email || undefined,
    phone: form.value.phone || undefined,
    baseSalary: form.value.amount ? { amount: form.value.amount, currencyCode: form.value.currencyCode } : undefined,
    linkedUserId: form.value.linkedUserId || undefined,
  };
}

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    const body = buildRequestBody();
    if (editingEmployeeId.value) {
      await updateEmployee(session.value.token, editingEmployeeId.value, body);
    } else {
      await createEmployee(session.value.token, body);
    }
    cancelEdit();
    await loadEmployees();
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

async function submitTerminate(employee: EmployeeSummary) {
  if (!session.value) return;
  if (!window.confirm(`Terminate "${employee.fullName}"? This cannot be undone — a rehire needs a new record.`)) return;

  terminateState.value[employee.id] = "submitting";
  terminateError.value[employee.id] = "";
  try {
    await terminateEmployee(session.value.token, employee.id, terminateReason.value[employee.id]);
    terminateState.value[employee.id] = "idle";
    if (statusFilter.value === "ACTIVE") {
      employees.value = employees.value.filter((existing) => existing.id !== employee.id);
    } else {
      await loadEmployees();
    }
  } catch (error) {
    handleApiError(error);
    terminateState.value[employee.id] = "error";
    terminateError.value[employee.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadEmployees();
});
</script>

<template>
  <WegoPageHeader title="Employees" description="Staff records, salaries, and terminations." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view employee records.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel title="Employee records" class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to list employees (hr:employee-view).
      </p>
      <template v-else>
        <div class="flex flex-wrap items-end gap-3">
          <WegoInput id="search" v-model="search" label="Search by name" class="min-w-0 flex-1" @keyup.enter="runSearch" />
          <WegoSelect id="statusFilter" v-model="statusFilter" label="Status" @change="runSearch">
            <option value="ACTIVE">Active</option>
            <option value="TERMINATED">Terminated</option>
            <option value="">All</option>
          </WegoSelect>
          <WegoButton type="button" variant="secondary" @click="runSearch">Search</WegoButton>
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && employees.length === 0 && page === 0" class="mt-3 text-sm text-wego-muted">
          No employee records yet.
        </p>
        <ul v-else-if="employees.length > 0" class="mt-4 space-y-3">
          <li v-for="employee in employees" :key="employee.id" class="rounded-wego-control border border-wego-border p-4">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <p class="font-semibold">{{ employee.fullName }}</p>
                  <WegoBadge :tone="employee.status === 'ACTIVE' ? 'success' : 'neutral'">{{ employee.status }}</WegoBadge>
                </div>
                <p class="mt-1 text-sm text-wego-muted">
                  {{ employee.position }}<span v-if="employee.department"> · {{ employee.department }}</span>
                </p>
              </div>
              <div v-if="canManage()" class="flex shrink-0 gap-2">
                <WegoButton type="button" variant="secondary" @click="startEdit(employee)">Edit</WegoButton>
              </div>
            </div>

            <WegoAlert v-if="terminateState[employee.id] === 'error'" variant="danger" class="mt-2">
              {{ terminateError[employee.id] }}
            </WegoAlert>

            <div v-if="canManage() && employee.status === 'ACTIVE'" class="mt-3 flex flex-wrap items-end gap-2">
              <WegoInput
                :id="`terminate-reason-${employee.id}`"
                :model-value="terminateReason[employee.id] ?? ''"
                label="Termination reason (optional)"
                class="min-w-0 flex-1"
                @update:model-value="(value) => (terminateReason[employee.id] = value)"
              />
              <WegoButton
                type="button"
                variant="secondary"
                :disabled="terminateState[employee.id] === 'submitting'"
                @click="submitTerminate(employee)"
              >
                Terminate
              </WegoButton>
            </div>
          </li>
        </ul>

        <div class="mt-4 flex items-center gap-3">
          <WegoButton type="button" variant="secondary" :disabled="page === 0" @click="previousPage">Previous</WegoButton>
          <span class="text-sm text-wego-muted">Page {{ page + 1 }}</span>
          <WegoButton type="button" variant="secondary" :disabled="!hasNextPage" @click="nextPage">Next</WegoButton>
        </div>
      </template>
    </WegoPanel>

    <WegoPanel v-if="canManage()" :title="editingEmployeeId ? 'Edit employee' : 'New employee'" class="mt-8">
      <form class="space-y-5" @submit.prevent="submitForm">
        <WegoInput id="fullName" v-model="form.fullName" label="Full name" required />
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="position" v-model="form.position" label="Position" required />
          <WegoInput id="department" v-model="form.department" label="Department (optional)" />
        </div>
        <WegoInput id="hireDate" v-model="form.hireDate" label="Hire date" type="date" required />
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="email" v-model="form.email" label="Email (optional)" type="email" />
          <WegoInput id="phone" v-model="form.phone" label="Phone (optional)" />
        </div>
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="amount" v-model="form.amount" label="Base salary amount (optional)" placeholder="15000.00" />
          <WegoInput id="currencyCode" v-model="form.currencyCode" label="Currency" />
        </div>
        <WegoInput id="linkedUserId" v-model="form.linkedUserId" label="Linked staff account id (optional)" />
        <p class="text-xs text-wego-muted">
          Linking connects this record to a login account (Accounts page) — must be an active staff user.
        </p>

        <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

        <div class="flex gap-3">
          <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
            {{ editingEmployeeId ? "Save changes" : "Create employee" }}
          </WegoButton>
          <WegoButton v-if="editingEmployeeId" type="button" variant="secondary" @click="cancelEdit">Cancel</WegoButton>
        </div>
      </form>
    </WegoPanel>
  </template>
</template>
