<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoInput, WegoPageHeader, WegoPanel, WegoSelect } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  approveLeaveRequest,
  cancelLeaveRequest,
  type EmployeeSummary,
  HrApiError,
  type LeaveRequest,
  type LeaveRequestStatus,
  type LeaveType,
  listEmployees,
  listLeaveRequests,
  rejectLeaveRequest,
  submitLeaveRequest,
} from "../composables/useHrApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Leave Requests · Wego Platform" });

const session = ref<AuthSession | null>(null);
const employees = ref<EmployeeSummary[]>([]);
const leaveRequests = ref<LeaveRequest[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const statusFilter = ref<LeaveRequestStatus | "">("PENDING");

const canManage = () => hasPermission(session.value, "hr:leave-manage");
const canView = () => hasPermission(session.value, "hr:leave-view");

const leaveTypes: LeaveType[] = ["ANNUAL", "SICK", "UNPAID", "OTHER"];

function employeeName(employeeId: string): string {
  return employees.value.find((employee) => employee.id === employeeId)?.fullName ?? employeeId;
}

function statusTone(status: LeaveRequestStatus): "success" | "danger" | "warning" | "neutral" {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  if (status === "PENDING") return "warning";
  return "neutral";
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
    if (error.errorCode === "overlaps_approved_leave") return "This employee already has another approved leave overlapping these dates.";
    if (error.errorCode === "not_pending") return "This request is no longer pending.";
    if (error.errorCode === "employee_not_active") return "That employee is terminated.";
    if (error.errorCode === "employee_not_found") return "That employee could not be found.";
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadAll() {
  if (!session.value) return;
  if (!canView()) {
    leaveRequests.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const [loadedEmployees, loadedRequests] = await Promise.all([
      listEmployees(session.value.token, { status: "ACTIVE" }),
      listLeaveRequests(session.value.token, { status: statusFilter.value || undefined }),
    ]);
    employees.value = loadedEmployees;
    leaveRequests.value = loadedRequests;
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
    leaveType: "ANNUAL" as LeaveType,
    startDate: "",
    endDate: "",
    reason: "",
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
    const submitted = await submitLeaveRequest(session.value.token, {
      employeeId: form.value.employeeId,
      leaveType: form.value.leaveType,
      startDate: form.value.startDate,
      endDate: form.value.endDate,
      reason: form.value.reason || undefined,
    });
    if (!statusFilter.value || statusFilter.value === "PENDING") {
      leaveRequests.value = [submitted, ...leaveRequests.value];
    }
    form.value = blankForm();
    formState.value = "idle";
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

const rowState = ref<Record<string, "idle" | "submitting" | "error">>({});
const rowError = ref<Record<string, string>>({});
const decisionNotes = ref<Record<string, string>>({});

function applyUpdate(updated: LeaveRequest) {
  if (statusFilter.value && statusFilter.value !== updated.status) {
    leaveRequests.value = leaveRequests.value.filter((existing) => existing.id !== updated.id);
  } else {
    leaveRequests.value = leaveRequests.value.map((existing) => (existing.id === updated.id ? updated : existing));
  }
}

async function decide(request: LeaveRequest, action: "approve" | "reject" | "cancel") {
  if (!session.value) return;
  rowState.value[request.id] = "submitting";
  rowError.value[request.id] = "";
  try {
    const updated =
      action === "approve"
        ? await approveLeaveRequest(session.value.token, request.id, decisionNotes.value[request.id])
        : action === "reject"
          ? await rejectLeaveRequest(session.value.token, request.id, decisionNotes.value[request.id])
          : await cancelLeaveRequest(session.value.token, request.id);
    applyUpdate(updated);
    rowState.value[request.id] = "idle";
  } catch (error) {
    handleApiError(error);
    rowState.value[request.id] = "error";
    rowError.value[request.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadAll();
});
</script>

<template>
  <WegoPageHeader title="Leave Requests" description="Time-off requests, approvals, and rejections." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view leave requests.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel title="Requests" class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to view leave requests (hr:leave-view).
      </p>
      <template v-else>
        <div class="flex flex-wrap items-end gap-3">
          <WegoSelect id="statusFilter" v-model="statusFilter" label="Status" @change="runFilter">
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="">All</option>
          </WegoSelect>
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && leaveRequests.length === 0" class="mt-3 text-sm text-wego-muted">
          No leave requests here.
        </p>
        <ul v-else class="mt-4 space-y-3">
          <li v-for="request in leaveRequests" :key="request.id" class="rounded-wego-control border border-wego-border p-4">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <p class="font-semibold">{{ employeeName(request.employeeId) }}</p>
                  <WegoBadge :tone="statusTone(request.status)">{{ request.status }}</WegoBadge>
                </div>
                <p class="mt-1 text-sm text-wego-muted">{{ request.leaveType }} · {{ request.startDate }} – {{ request.endDate }}</p>
                <p v-if="request.reason" class="mt-1 text-sm text-wego-muted">{{ request.reason }}</p>
              </div>
            </div>
            <WegoAlert v-if="rowState[request.id] === 'error'" variant="danger" class="mt-2">{{ rowError[request.id] }}</WegoAlert>

            <div v-if="canManage() && request.status === 'PENDING'" class="mt-3 flex flex-wrap items-end gap-2">
              <WegoInput
                :id="`decision-notes-${request.id}`"
                :model-value="decisionNotes[request.id] ?? ''"
                label="Notes (optional)"
                class="min-w-0 flex-1"
                @update:model-value="(value) => (decisionNotes[request.id] = value)"
              />
              <WegoButton
                type="button"
                :disabled="rowState[request.id] === 'submitting'"
                @click="decide(request, 'approve')"
              >
                Approve
              </WegoButton>
              <WegoButton
                type="button"
                variant="secondary"
                :disabled="rowState[request.id] === 'submitting'"
                @click="decide(request, 'reject')"
              >
                Reject
              </WegoButton>
              <WegoButton
                type="button"
                variant="secondary"
                :disabled="rowState[request.id] === 'submitting'"
                @click="decide(request, 'cancel')"
              >
                Cancel
              </WegoButton>
            </div>
          </li>
        </ul>
      </template>
    </WegoPanel>

    <WegoPanel v-if="canManage()" title="New leave request" class="mt-8">
      <form class="space-y-5" @submit.prevent="submitForm">
        <WegoSelect id="employeeId" v-model="form.employeeId" label="Employee" required>
          <option value="" disabled>Select an employee…</option>
          <option v-for="employee in employees" :key="employee.id" :value="employee.id">{{ employee.fullName }}</option>
        </WegoSelect>
        <WegoSelect id="leaveType" v-model="form.leaveType" label="Leave type">
          <option v-for="type in leaveTypes" :key="type" :value="type">{{ type }}</option>
        </WegoSelect>
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="startDate" v-model="form.startDate" label="Start date" type="date" required />
          <WegoInput id="endDate" v-model="form.endDate" label="End date" type="date" required />
        </div>
        <WegoInput id="reason" v-model="form.reason" label="Reason (optional)" />

        <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

        <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
          Submit request
        </WegoButton>
      </form>
    </WegoPanel>
  </template>
</template>
