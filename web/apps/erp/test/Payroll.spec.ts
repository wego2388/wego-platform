import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import PayrollPage from "../app/pages/payroll.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleEmployee = {
  id: "99999999-9999-9999-9999-999999999999",
  fullName: "Nour Hassan",
  position: "Dive Instructor",
  status: "ACTIVE",
};

const draftRun = {
  id: "dddddddd-dddd-dddd-dddd-dddddddddddd",
  payPeriodStart: "2026-08-01",
  payPeriodEnd: "2026-08-31",
  currencyCode: "EGP",
  totalAmount: "15000.00",
  status: "DRAFT",
  lines: [{ employeeId: sampleEmployee.id, amount: "15000.00" }],
  createdAt: "2026-08-31T00:00:00Z",
};

function fetchMockFor(runs: unknown[] = [draftRun]) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if (init?.method === "POST" && url.pathname === "/api/v1/payroll/runs") {
      return new Response(JSON.stringify(draftRun), { status: 201 });
    }
    if (url.pathname === "/api/v1/hr/employees") {
      return new Response(JSON.stringify([sampleEmployee]), { status: 200 });
    }
    if (url.pathname === "/api/v1/payroll/runs") {
      return new Response(JSON.stringify(runs), { status: 200 });
    }
    return new Response(JSON.stringify([]), { status: 200 });
  });
}

describe("payroll page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(PayrollPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists payroll runs with resolved employee names for each line", async () => {
    seedSession(["payroll:view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(PayrollPage);
    await flushPromises();

    expect(wrapper.text()).toContain("2026-08-01 – 2026-08-31 · DRAFT");
    expect(wrapper.text()).toContain("15000.00 EGP across 1 employees");
    expect(wrapper.text()).toContain("Nour Hassan — 15000.00 EGP");
  });

  it("hides the create form and post/discard actions for a user without payroll:manage", async () => {
    seedSession(["payroll:view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(PayrollPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New payroll run");
    expect(wrapper.findAll("button").some((button) => button.text() === "Post")).toBe(false);
  });

  it("creates a new draft payroll run", async () => {
    seedSession(["payroll:view", "payroll:manage"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/payroll/runs") {
        return new Response(JSON.stringify(draftRun), { status: 201 });
      }
      if (url.pathname === "/api/v1/hr/employees") {
        return new Response(JSON.stringify([sampleEmployee]), { status: 200 });
      }
      if (url.pathname === "/api/v1/payroll/runs") {
        return new Response(JSON.stringify([draftRun]), { status: 200 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(PayrollPage);
    await flushPromises();

    await wrapper.get("#payPeriodStart").setValue("2026-08-01");
    await wrapper.get("#payPeriodEnd").setValue("2026-08-31");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("Nour Hassan — 15000.00 EGP");
  });

  it("posts a draft run", async () => {
    seedSession(["payroll:view", "payroll:manage"]);
    const posted = { ...draftRun, status: "POSTED", journalEntryId: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee" };
    let isPosted = false;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === `/api/v1/payroll/runs/${draftRun.id}/post`) {
        isPosted = true;
        return new Response(JSON.stringify(posted), { status: 200 });
      }
      if (url.pathname === "/api/v1/hr/employees") {
        return new Response(JSON.stringify([sampleEmployee]), { status: 200 });
      }
      if (url.pathname === "/api/v1/payroll/runs") {
        return new Response(JSON.stringify([isPosted ? posted : draftRun]), { status: 200 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(PayrollPage);
    await flushPromises();

    const postButton = wrapper.findAll("button").find((button) => button.text() === "Post");
    await postButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("POSTED");
  });
});
