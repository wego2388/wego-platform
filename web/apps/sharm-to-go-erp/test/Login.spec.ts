import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import LoginPage from "../app/pages/login.vue";

// The login flow itself (submit/logout/orphaned-session handling) is a
// byte-for-byte behavioral copy of web/apps/erp/app/pages/login.vue — this
// suite mirrors that app's own Login.spec.ts for the same reason: two
// separate, isolated client applications (see WEGO-010-A Packet 0R)
// deliberately duplicating the same proven identity-flow logic, not
// importing a shared page component across client boundaries.

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("login page", () => {
  it("submits credentials and shows the authenticated identity on success", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/v1/identity/login")) {
        return new Response(JSON.stringify({ token: "test-token", expiresAt: "2026-08-09T12:00:00Z" }), {
          status: 200,
        });
      }
      if (url.endsWith("/api/v1/identity/me")) {
        return new Response(
          JSON.stringify({
            userId: "11111111-1111-1111-1111-111111111111",
            email: "admin@example.com",
            roles: ["platform-admin"],
            permissions: ["identity:administer"],
          }),
          { status: 200 },
        );
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(LoginPage);
    await wrapper.get("#email").setValue("admin@example.com");
    await wrapper.get("#password").setValue("correct-password");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("Signed in as admin@example.com");
    expect(wrapper.text()).toContain("identity:administer");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("shows an inline error and never renders the identity panel on invalid credentials", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ error: "invalid_credentials" }), { status: 401 })),
    );

    const wrapper = mount(LoginPage);
    await wrapper.get("#email").setValue("admin@example.com");
    await wrapper.get("#password").setValue("wrong-password");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toBe("Incorrect email or password.");
    expect(wrapper.text()).not.toContain("Signed in as");
  });

  it("signs out, calling the logout endpoint and returning to the form", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/v1/identity/login")) {
        return new Response(JSON.stringify({ token: "test-token", expiresAt: "2026-08-09T12:00:00Z" }), {
          status: 200,
        });
      }
      if (url.endsWith("/api/v1/identity/me")) {
        return new Response(
          JSON.stringify({
            userId: "11111111-1111-1111-1111-111111111111",
            email: "admin@example.com",
            roles: ["platform-admin"],
            permissions: ["identity:administer"],
          }),
          { status: 200 },
        );
      }
      if (url.endsWith("/api/v1/identity/logout")) {
        return new Response(null, { status: 204 });
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(LoginPage);
    await wrapper.get("#email").setValue("admin@example.com");
    await wrapper.get("#password").setValue("correct-password");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    await wrapper.get("button[type=button]").trigger("click");
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[2]?.[0]).toContain("/api/v1/identity/logout");
    expect(wrapper.text()).not.toContain("Signed in as");
    expect(wrapper.find("#email").exists()).toBe(true);
    expect(wrapper.find('[role="status"]').exists()).toBe(false);
  });

  it("recovers the form instead of staying stuck on 'Signing in…' when the network request itself fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("Failed to fetch");
      }),
    );

    const wrapper = mount(LoginPage);
    await wrapper.get("#email").setValue("admin@example.com");
    await wrapper.get("#password").setValue("correct-password");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    const submitButton = wrapper.get("button[type=submit]");
    expect(submitButton.attributes("disabled")).toBeUndefined();
    expect(wrapper.get('[role="alert"]').text()).toContain("Could not reach the server");
  });
});
