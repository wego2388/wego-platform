import { describe, expect, it } from "vitest";
import { IdentityAdminApiError } from "../app/composables/useIdentityAdminApi";
import { groupPermissions, staffErrorText } from "../app/composables/useStaffAdmin";

describe("staff admin helpers", () => {
  it("groups namespaced and plain permission codes by resource", () => {
    const groups = groupPermissions([
      { code: "tours-operator.booking:view", description: "View bookings" },
      { code: "identity:user-view", description: "View staff" },
      { code: "tours-operator.booking:cancel", description: "Cancel bookings" },
      { code: "tours-operator.payment:view", description: "View payments" },
    ]);
    expect(groups.map((group) => group.label)).toEqual([
      "Bookings",
      "Payments & finance",
      "Staff accounts & roles",
    ]);
    expect(groups[0]?.permissions.map((permission) => permission.code)).toEqual([
      "tours-operator.booking:cancel",
      "tours-operator.booking:view",
    ]);
  });

  it("explains the self-protection rules in plain words", () => {
    expect(staffErrorText(new IdentityAdminApiError(400, "cannot_disable_self"))).toBe("You can't disable your own account.");
    expect(staffErrorText(new IdentityAdminApiError(400, "cannot_change_own_roles"))).toContain("ask another admin");
    expect(staffErrorText(new IdentityAdminApiError(400, "unknown_role:ghost"))).toBe("Unknown role: ghost");
    expect(staffErrorText(new IdentityAdminApiError(403, "forbidden"))).toBe("You don't have permission for this.");
    expect(staffErrorText(new TypeError("offline"))).toContain("Could not reach the server");
  });
});
