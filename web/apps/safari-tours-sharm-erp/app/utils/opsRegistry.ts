import type { AssignmentIssue } from "@wego/api-contract";
import { ToursApiError } from "../composables/useToursApi";
import type { ErpMessageKey } from "./erpLocale";
import type { ErpMessageDescriptor } from "./bookingMessages";

/** A registry write failed: translate the API's code. A stale revision gets its own message and a reload. */
export function registrySaveError(error: unknown): ErpMessageDescriptor {
  if (!(error instanceof ToursApiError)) return { key: "common.connectionFailed" };
  if (error.status === 401) return { key: "common.sessionExpired" };
  if (error.status === 403) return { key: "common.forbidden" };
  switch (error.errorCode) {
    case "revision_conflict": return { key: "ops.revisionConflict" };
    case "supplier_code_taken": return { key: "ops.codeTaken" };
    case "vehicle_plate_taken": return { key: "ops.plateTaken" };
    case "tour_not_found": return { key: "ops.tourNotFound" };
    case "supplier_not_found": return { key: "ops.supplierNotFound" };
    case "validation_failed": return { key: "ops.invalid" };
    default: return { key: "ops.saveFailed", params: { code: error.errorCode } };
  }
}

export interface AssignmentFailure {
  message: ErpMessageDescriptor;
  /** Conflicts to show beside the form: kind plus the other departure's id. */
  conflicts: { kind: "DRIVER" | "VEHICLE"; otherSlotId: string; otherTimeSlot: string }[];
  revisionConflict: boolean;
}

const ASSIGNMENT_ERRORS = new Set([
  "revision_conflict", "slot_in_past", "driver_not_found", "vehicle_not_found", "supplier_not_found",
  "driver_inactive", "driver_licence_expired", "vehicle_inactive", "supplier_inactive",
]);

export function assignmentFailure(error: unknown): AssignmentFailure {
  const none = { conflicts: [], revisionConflict: false };
  if (!(error instanceof ToursApiError)) return { ...none, message: { key: "common.connectionFailed" } };
  if (error.status === 401) return { ...none, message: { key: "common.sessionExpired" } };
  if (error.status === 403) return { ...none, message: { key: "common.forbidden" } };
  if (error.errorCode === "assignment_conflict") {
    const body = error.body as { conflicts?: AssignmentFailure["conflicts"] } | null;
    return { ...none, conflicts: body?.conflicts ?? [], message: { key: "asg.conflictTitle" } };
  }
  if (ASSIGNMENT_ERRORS.has(error.errorCode)) {
    return { ...none, revisionConflict: error.errorCode === "revision_conflict", message: { key: `asg.err.${error.errorCode}` as ErpMessageKey } };
  }
  return { ...none, message: { key: "asg.err.generic", params: { code: error.errorCode } } };
}

export const isBlocking = (issue: AssignmentIssue) => issue.blocking;
export const issueMessage = (issue: AssignmentIssue): ErpMessageDescriptor => ({
  key: `asg.issue.${issue.code}` as ErpMessageKey,
  params: { value: issue.value ?? 0, limit: issue.limit ?? 0 },
});

/** The licence state shown in the driver list for a given day (ISO date). */
export function licenceState(validUntil: string, today: string): "expired" | "soon" | "ok" {
  if (validUntil < today) return "expired";
  const limit = new Date(`${today}T00:00:00Z`);
  limit.setUTCDate(limit.getUTCDate() + 30);
  return validUntil < limit.toISOString().slice(0, 10) ? "soon" : "ok";
}

/** A driver-sheet or supplier-order link carries only internal ids. */
export const supplierOrderSubject = (slotId: string, supplierId: string) => `${slotId}_${supplierId}`;
export function splitSupplierOrderSubject(subject: string): [string, string] | null {
  const parts = subject.split("_");
  return parts.length === 2 && parts[0] && parts[1] ? [parts[0], parts[1]] : null;
}
