import { IdentityAdminApiError } from "../composables/useIdentityAdminApi";
import type { ErpMessageDescriptor } from "./bookingMessages";
import type { ErpMessageKey } from "./erpLocale";

export function staffErrorMessage(error: unknown): ErpMessageDescriptor {
  if (!(error instanceof IdentityAdminApiError)) return { key: "common.connectionFailed" };
  if (error.status === 401) return { key: "common.sessionExpired" };
  if (error.status === 403) return { key: "common.forbidden" };
  const keys: Record<string, ErpMessageKey> = {
    email_already_in_use: "staff.emailUsed", cannot_disable_self: "staff.disableSelf",
    cannot_change_own_roles: "staff.ownRoles", role_already_exists: "staff.roleExists",
  };
  if (Object.hasOwn(keys, error.errorCode)) return { key: keys[error.errorCode]! };
  if (error.errorCode.startsWith("unknown_role:")) return { key: "staff.unknownRole", params: { code: error.errorCode.slice("unknown_role:".length) } };
  if (error.errorCode.startsWith("unknown_permission:")) return { key: "staff.unknownPermission", params: { code: error.errorCode.slice("unknown_permission:".length) } };
  if (error.status === 404) return { key: "staff.notFound" };
  return { key: "common.requestFailed", params: { code: error.errorCode } };
}

export function staffGroupKey(label: string): ErpMessageKey | null {
  const keys: Record<string, ErpMessageKey> = {
    Bookings: "staff.group.booking", "Tours & slots": "staff.group.tour",
    "Payments & finance": "staff.group.payment", "Staff accounts & roles": "staff.group.identity",
  };
  return Object.hasOwn(keys, label) ? keys[label]! : null;
}
