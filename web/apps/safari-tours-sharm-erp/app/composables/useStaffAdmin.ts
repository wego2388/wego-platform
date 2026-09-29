/**
 * Pure helpers for the staff accounts & roles page. Kept separate from the
 * page so error wording and permission grouping are unit-testable.
 */
import { IdentityAdminApiError, type Permission } from "./useIdentityAdminApi";

export const MIN_PASSWORD_LENGTH = 12;

export function staffErrorText(error: unknown): string {
  if (error instanceof IdentityAdminApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "email_already_in_use") return "That email is already used by another account.";
    if (error.errorCode === "cannot_disable_self") return "You can't disable your own account.";
    if (error.errorCode === "cannot_change_own_roles") return "You can't change your own roles — ask another admin.";
    if (error.errorCode === "role_already_exists") return "A role with that code already exists.";
    if (error.errorCode.startsWith("unknown_role:")) return `Unknown role: ${error.errorCode.split(":")[1]}`;
    if (error.errorCode.startsWith("unknown_permission:")) return `Unknown permission: ${error.errorCode.split(":")[1]}`;
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

export interface PermissionGroup {
  label: string;
  permissions: Permission[];
}

const GROUP_LABELS: Record<string, string> = {
  booking: "Bookings",
  tour: "Tours & slots",
  payment: "Payments & finance",
  identity: "Staff accounts & roles",
};

/**
 * Groups codes such as `tours-operator.booking:cancel` or `identity:user-view`
 * by the resource they control, so a role editor reads like a checklist.
 */
export function groupPermissions(permissions: Permission[]): PermissionGroup[] {
  const groups = new Map<string, Permission[]>();
  for (const permission of permissions) {
    const beforeAction = permission.code.split(":")[0] ?? permission.code;
    const resource = beforeAction.includes(".") ? beforeAction.split(".").pop()! : beforeAction;
    groups.set(resource, [...(groups.get(resource) ?? []), permission]);
  }
  return Array.from(groups.entries())
    .map(([resource, items]) => ({
      label: GROUP_LABELS[resource] ?? resource,
      permissions: [...items].sort((left, right) => left.code.localeCompare(right.code)),
    }))
    .sort((left, right) => left.label.localeCompare(right.label));
}
