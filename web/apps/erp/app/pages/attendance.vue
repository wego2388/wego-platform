<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoInput, WegoPageHeader, WegoPanel, WegoSelect } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  type AttendanceRecord,
  type AttendanceStatus,
  type EmployeeSummary,
  HrApiError,
  listAttendance,
  listEmployees,
  recordAttendance,
} from "../composables/useHrApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Attendance · Wego Platform" });

const session = ref<AuthSession | null>(null);
const employees = ref<EmployeeSummary[]>([]);
const records = ref<AttendanceRecord[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const employeeFilter = ref("");

const canManage = () => hasPermission(session.value, "hr:attendance-manage");
const canView = () => hasPermission(session.value, "hr:attendance-view");

const attendanceStatuses: AttendanceStatus[] = ["PRESENT", "ABSENT", "LATE", "HALF_DAY"];

function statusTone(status: AttendanceStatus): "success" | "danger" | "warning" | "neutral" {
  if (status === "PRESENT") return "success";
  if (status === "ABSENT") return "danger";
  if (status === "LATE" || status === "HALF_DAY") return "warning";
  return "neutral";
}

function employeeName(employeeId: string): string {
  return employees.value.find((employee) => employee.id === employeeId)?.fullName ?? employeeId;
}

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
    if (error.errorCode === "employee_not_active") return "That employee is terminated — attendance can't be recorded for them.";
    if (error.errorCode === "attendance_date_in_future") return "Attendance can't be recorded for a future date.";
    if (error.errorCode === "employee_not_found") return "That employee could not be found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadAll() {
  if (!session.value) return;
  if (!canView()) {
    records.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const [loadedEmployees, loadedRecords] = await Promise.all([
      listEmployees(session.value.token, { status: "ACTIVE" }),
      listAttendance(session.value.token, { employeeId: employeeFilter.value || undefined }),
    ]);
    employees.value = loadedEmployees;
    records.value = loadedRecords;
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
  return {
    employeeId: "",
    attendanceDate: "",
    status: "PRESENT" as AttendanceStatus,
    notes: "",
  };
}

const form = ref(blankForm());
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    const recorded = await recordAttendance(session.value.token, {
      employeeId: form.value.employeeId,
      attendanceDate: form.value.attendanceDate,
      status: form.value.status,
      notes: form.value.notes || undefined,
    });
    records.value = [recorded, ...records.value.filter((existing) => existing.id !== recorded.id)];
    form.value = blankForm();
    formState.value = "idle";
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadAll();
});
</script>

<template>
  <WegoPageHeader title="Attendance" description="Daily presence records for the active roster." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view attendance records.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel title="Attendance records" class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to view attendance records (hr:attendance-view).
      </p>
      <template v-else>
        <div class="flex flex-wrap items-end gap-3">
          <WegoSelect id="employeeFilter" v-model="employeeFilter" label="Employee" @change="runFilter">
            <option value="">All employees</option>
            <option v-for="employee in employees" :key="employee.id" :value="employee.id">{{ employee.fullName }}</option>
          </WegoSelect>
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && records.length === 0" class="mt-3 text-sm text-wego-muted">
          No attendance records yet.
        </p>
        <ul v-else class="mt-4 space-y-3">
          <li v-for="record in records" :key="record.id" class="rounded-wego-control border border-wego-border p-4">
            <p class="font-semibold">{{ employeeName(record.employeeId) }}</p>
            <p class="mt-1 flex items-center gap-2 text-sm text-wego-muted">
              {{ record.attendanceDate }}
              <WegoBadge :tone="statusTone(record.status)">{{ record.status }}</WegoBadge>
              <span v-if="record.notes">· {{ record.notes }}</span>
            </p>
          </li>
        </ul>
      </template>
    </WegoPanel>

    <WegoPanel v-if="canManage()" title="Record attendance" description="Recording again for the same employee and date corrects that day's record." class="mt-8">
      <form class="space-y-5" @submit.prevent="submitForm">
        <WegoSelect id="employeeId" v-model="form.employeeId" label="Employee" required>
          <option value="" disabled>Select an employee…</option>
          <option v-for="employee in employees" :key="employee.id" :value="employee.id">{{ employee.fullName }}</option>
        </WegoSelect>
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="attendanceDate" v-model="form.attendanceDate" label="Date" type="date" required />
          <WegoSelect id="status" v-model="form.status" label="Status">
            <option v-for="status in attendanceStatuses" :key="status" :value="status">{{ status }}</option>
          </WegoSelect>
        </div>
        <WegoInput id="notes" v-model="form.notes" label="Notes (optional)" />

        <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

        <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
          Record attendance
        </WegoButton>
      </form>
    </WegoPanel>
  </template>
</template>
