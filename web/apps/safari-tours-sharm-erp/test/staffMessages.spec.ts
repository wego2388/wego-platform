import { describe, expect, it } from "vitest";
import { IdentityAdminApiError } from "../app/composables/useIdentityAdminApi";
import { staffErrorMessage, staffGroupKey } from "../app/utils/staffMessages";
import { erpMessage } from "../app/utils/erpLocale";
describe("staff bilingual display boundaries", () => {
  it.each(["email_already_in_use", "cannot_disable_self", "cannot_change_own_roles", "role_already_exists"])("translates %s without changing its business meaning", (code) => {
    const message = staffErrorMessage(new IdentityAdminApiError(400, code));
    expect(message.key).toMatch(/^staff\./);
    expect(erpMessage("ar", message.key)).not.toContain(code);
  });
  it.each([401, 403, 404, 500])("retains localized HTTP %s errors", (status) => {
    const message = staffErrorMessage(new IdentityAdminApiError(status, "original_code"));
    expect(erpMessage("ar", message.key, message.params)).not.toBe(erpMessage("en", message.key, message.params));
  });
  it("retains unknown role/permission text including colons without treating it as markup", () => {
    expect(staffErrorMessage(new IdentityAdminApiError(400, "unknown_role:code:part"))).toEqual({ key: "staff.unknownRole", params: { code: "code:part" } });
    expect(staffErrorMessage(new IdentityAdminApiError(400, "unknown_permission:<script>"))).toEqual({ key: "staff.unknownPermission", params: { code: "<script>" } });
    expect(staffErrorMessage(new IdentityAdminApiError(500, "__proto__"))).toEqual({ key: "common.requestFailed", params: { code: "__proto__" } });
    expect(staffErrorMessage(new Error())).toEqual({ key: "common.connectionFailed" });
  });
  it("localizes known groups without inventing a meaning for a new permission resource", () => {
    expect(staffGroupKey("Bookings")).toBe("staff.group.booking");
    expect(staffGroupKey("__proto__")).toBeNull();
    expect(staffGroupKey("future-resource")).toBeNull();
  });
});
