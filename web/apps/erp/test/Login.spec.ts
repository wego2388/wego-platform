import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import LoginPage from "../app/pages/login.vue";

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

  it("shows the real wait time from Retry-After when the login is rate-limited", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(JSON.stringify({ error: "rate_limited" }), {
            status: 429,
            headers: { "Retry-After": "120" },
          }),
      ),
    );

    const wrapper = mount(LoginPage);
    await wrapper.get("#email").setValue("admin@example.com");
    await wrapper.get("#password").setValue("whatever");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    // Not a generic message that implies retrying now is reasonable — the
    // account throttle's wait genuinely varies (2 minutes here), so the
    // message must reflect the real Retry-After value, not a fixed string.
    expect(wrapper.get('[role="alert"]').text()).toBe("Too many attempts. Try again in 2 minutes.");
    expect(wrapper.text()).not.toContain("Signed in as");
  });

  it("falls back to a generic rate-limit message when Retry-After is missing", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ error: "rate_limited" }), { status: 429 })),
    );

    const wrapper = mount(LoginPage);
    await wrapper.get("#email").setValue("admin@example.com");
    await wrapper.get("#password").setValue("whatever");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toBe("Too many attempts. Please wait a moment before trying again.");
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

  it("best-effort revokes the session and clears the token when /me fails after a successful login", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/v1/identity/login")) {
        return new Response(JSON.stringify({ token: "orphaned-token", expiresAt: "2026-08-09T12:00:00Z" }), {
          status: 200,
        });
      }
      if (url.endsWith("/api/v1/identity/me")) {
        return new Response(null, { status: 401 });
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

    // The orphaned session must have been cancelled server-side, not just
    // forgotten locally.
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const logoutCall = fetchMock.mock.calls[2];
    expect(logoutCall?.[0]).toContain("/api/v1/identity/logout");
    expect((logoutCall?.[1]?.headers as Record<string, string>).Authorization).toBe("Bearer orphaned-token");

    expect(wrapper.get('[role="alert"]').text()).not.toBe("");
    expect(wrapper.text()).not.toContain("Signed in as");
    // No stale token left behind: the "Sign out" button (only rendered on
    // the success panel) must not appear, and a retry must not silently
    // reuse the orphaned token instead of asking the server for a fresh one.
    expect(wrapper.find("button[type=button]").exists()).toBe(false);
    expect(wrapper.find("#email").exists()).toBe(true);
  });

  it("warns when /me fails after login and the best-effort revoke of the orphaned session also fails", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/v1/identity/login")) {
        return new Response(JSON.stringify({ token: "orphaned-token", expiresAt: "2026-08-09T12:00:00Z" }), {
          status: 200,
        });
      }
      if (url.endsWith("/api/v1/identity/me")) {
        return new Response(null, { status: 401 });
      }
      if (url.endsWith("/api/v1/identity/logout")) {
        throw new TypeError("Failed to fetch");
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(LoginPage);
    await wrapper.get("#email").setValue("admin@example.com");
    await wrapper.get("#password").setValue("correct-password");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.get('[role="status"]').text()).toContain("didn't confirm the session was revoked");
    expect(wrapper.text()).not.toContain("Signed in as");
  });

  it("best-effort revokes the session when /me itself throws a network error after a successful login", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/v1/identity/login")) {
        return new Response(JSON.stringify({ token: "orphaned-token", expiresAt: "2026-08-09T12:00:00Z" }), {
          status: 200,
        });
      }
      if (url.endsWith("/api/v1/identity/me")) {
        throw new TypeError("Failed to fetch");
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

    // A thrown /me must not be treated as "nothing happened" — /login
    // already succeeded, so a session exists server-side and must be
    // cancelled the same as an explicit non-2xx /me response would be.
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const logoutCall = fetchMock.mock.calls[2];
    expect(logoutCall?.[0]).toContain("/api/v1/identity/logout");
    expect((logoutCall?.[1]?.headers as Record<string, string>).Authorization).toBe("Bearer orphaned-token");

    expect(wrapper.get('[role="alert"]').text()).toContain("Could not reach the server");
    expect(wrapper.text()).not.toContain("Signed in as");
    expect(wrapper.find("button[type=button]").exists()).toBe(false);
    expect(wrapper.find("#email").exists()).toBe(true);
  });

  it("warns when /me throws a network error and the best-effort revoke of the orphaned session also fails", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/v1/identity/login")) {
        return new Response(JSON.stringify({ token: "orphaned-token", expiresAt: "2026-08-09T12:00:00Z" }), {
          status: 200,
        });
      }
      if (url.endsWith("/api/v1/identity/me")) {
        throw new TypeError("Failed to fetch");
      }
      if (url.endsWith("/api/v1/identity/logout")) {
        throw new TypeError("Failed to fetch");
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(LoginPage);
    await wrapper.get("#email").setValue("admin@example.com");
    await wrapper.get("#password").setValue("correct-password");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.get('[role="status"]').text()).toContain("didn't confirm the session was revoked");
    expect(wrapper.text()).not.toContain("Signed in as");
  });

  it("warns instead of silently pretending success when the server doesn't confirm logout", async () => {
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
        throw new TypeError("Failed to fetch");
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

    expect(wrapper.text()).not.toContain("Signed in as");
    expect(wrapper.get('[role="status"]').text()).toContain("didn't confirm the session was revoked");
  });
});
