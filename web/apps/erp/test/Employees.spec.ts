import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import EmployeesPage from "../app/pages/employees.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleEmployeeSummary = {
  id: "33333333-3333-3333-3333-333333333333",
  fullName: "Layla Hassan",
  position: "Dive Instructor",
  department: "Operations",
  status: "ACTIVE",
};

const sampleEmployeeFull = {
  ...sampleEmployeeSummary,
  hireDate: "2026-01-01",
  email: "layla@example.com",
  phone: "+201000000000",
  baseSalary: { amount: "15000.00", currencyCode: "EGP" },
  createdAt: "2026-08-30T00:00:00Z",
};

describe("employees page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(EmployeesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists employee records for an authenticated user, without exposing salary in the roster", async () => {
    seedSession(["hr:employee-view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleEmployeeSummary]), { status: 200 })));

    const wrapper = mount(EmployeesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Layla Hassan");
    expect(wrapper.text()).toContain("Dive Instructor");
    expect(wrapper.text()).toContain("Operations");
    expect(wrapper.html()).not.toContain("15000.00");
  });

  it("filters by status ACTIVE by default when listing", async () => {
    seedSession(["hr:employee-view"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      expect(url.searchParams.get("status")).toBe("ACTIVE");
      return new Response(JSON.stringify([sampleEmployeeSummary]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    mount(EmployeesPage);
    await flushPromises();

    expect(fetchMock).toHaveBeenCalled();
  });

  it("hides the employee form and terminate button for a user without hr:employee-manage", async () => {
    seedSession(["hr:employee-view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleEmployeeSummary]), { status: 200 })));

    const wrapper = mount(EmployeesPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New employee");
    expect(wrapper.findAll("button").some((button) => button.text() === "Terminate")).toBe(false);
  });

  it("submits a new employee and refreshes the roster", async () => {
    seedSession(["hr:employee-view", "hr:employee-manage"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/hr/employees") {
        return new Response(JSON.stringify({ ...sampleEmployeeFull, id: "new-employee-id", fullName: "New Employee" }), {
          status: 201,
        });
      }
      return new Response(JSON.stringify([{ ...sampleEmployeeSummary, id: "new-employee-id", fullName: "New Employee" }]), {
        status: 200,
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(EmployeesPage);
    await flushPromises();

    await wrapper.get("#fullName").setValue("New Employee");
    await wrapper.get("#position").setValue("Front Desk");
    await wrapper.get("#hireDate").setValue("2026-08-30");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("New Employee");
  });

  it("fetches the full employee record (including salary) when editing, since the list only carries the roster projection", async () => {
    seedSession(["hr:employee-view", "hr:employee-manage"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      if (url.pathname === `/api/v1/hr/employees/${sampleEmployeeSummary.id}`) {
        return new Response(JSON.stringify(sampleEmployeeFull), { status: 200 });
      }
      return new Response(JSON.stringify([sampleEmployeeSummary]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(EmployeesPage);
    await flushPromises();

    const editButton = wrapper.findAll("button").find((button) => button.text() === "Edit");
    await editButton?.trigger("click");
    await flushPromises();

    const amountField = wrapper.get("#amount").element as HTMLInputElement;
    expect(amountField.value).toBe("15000.00");
  });

  it("terminates an employee after confirmation and removes it from the active list", async () => {
    seedSession(["hr:employee-view", "hr:employee-manage"]);
    vi.stubGlobal("confirm", vi.fn(() => true));
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === `/api/v1/hr/employees/${sampleEmployeeSummary.id}/terminate`) {
        return new Response(JSON.stringify({ ...sampleEmployeeFull, status: "TERMINATED", terminatedAt: "2026-08-30T00:00:00Z" }), {
          status: 200,
        });
      }
      return new Response(JSON.stringify([sampleEmployeeSummary]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(EmployeesPage);
    await flushPromises();

    const terminateButton = wrapper.findAll("button").find((button) => button.text() === "Terminate");
    await terminateButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).not.toContain("Layla Hassan");
  });
});
