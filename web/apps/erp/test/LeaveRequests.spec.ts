import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import LeaveRequestsPage from "../app/pages/leave-requests.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleEmployee = {
  id: "66666666-6666-6666-6666-666666666666",
  fullName: "Mona Sami",
  position: "Accountant",
  status: "ACTIVE",
};

const samplePending = {
  id: "77777777-7777-7777-7777-777777777777",
  employeeId: sampleEmployee.id,
  leaveType: "ANNUAL",
  startDate: "2026-09-01",
  endDate: "2026-09-05",
  reason: "Family trip",
  status: "PENDING",
  requestedAt: "2026-08-30T00:00:00Z",
};

function fetchMockFor(requests: unknown[] = [samplePending]) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if (init?.method === "POST" && url.pathname === "/api/v1/hr/leave-requests") {
      return new Response(JSON.stringify(samplePending), { status: 201 });
    }
    if (init?.method === "POST" && url.pathname === `/api/v1/hr/leave-requests/${samplePending.id}/approve`) {
      return new Response(JSON.stringify({ ...samplePending, status: "APPROVED", decidedAt: "2026-08-31T00:00:00Z" }), { status: 200 });
    }
    if (url.pathname === "/api/v1/hr/employees") {
      return new Response(JSON.stringify([sampleEmployee]), { status: 200 });
    }
    if (url.pathname === "/api/v1/hr/leave-requests") {
      return new Response(JSON.stringify(requests), { status: 200 });
    }
    return new Response(JSON.stringify([]), { status: 200 });
  });
}

describe("leave requests page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(LeaveRequestsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists pending leave requests by default with the employee's name resolved", async () => {
    seedSession(["hr:leave-view"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      if (url.pathname === "/api/v1/hr/leave-requests") {
        expect(url.searchParams.get("status")).toBe("PENDING");
        return new Response(JSON.stringify([samplePending]), { status: 200 });
      }
      return new Response(JSON.stringify([sampleEmployee]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(LeaveRequestsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Mona Sami");
    expect(wrapper.text()).toContain("ANNUAL");
    expect(wrapper.text()).toContain("Family trip");
  });

  it("hides the submit form and decision buttons for a user without hr:leave-manage", async () => {
    seedSession(["hr:leave-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(LeaveRequestsPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New leave request");
    expect(wrapper.findAll("button").some((button) => button.text() === "Approve")).toBe(false);
  });

  it("submits a new leave request", async () => {
    seedSession(["hr:leave-view", "hr:leave-manage"]);
    vi.stubGlobal("fetch", fetchMockFor([]));

    const wrapper = mount(LeaveRequestsPage);
    await flushPromises();

    await wrapper.get("#employeeId").setValue(sampleEmployee.id);
    await wrapper.get("#startDate").setValue("2026-09-01");
    await wrapper.get("#endDate").setValue("2026-09-05");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    // "Mona Sami" also appears in the form's own employee dropdown — the
    // reason text only appears once the submitted request renders as a card.
    expect(wrapper.text()).toContain("Family trip");
  });

  it("approves a pending request and it disappears from the default PENDING filter", async () => {
    seedSession(["hr:leave-view", "hr:leave-manage"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(LeaveRequestsPage);
    await flushPromises();

    const approveButton = wrapper.findAll("button").find((button) => button.text() === "Approve");
    await approveButton?.trigger("click");
    await flushPromises();

    // "Mona Sami" also appears in the New-request form's employee dropdown —
    // scope to the reason text, which only ever appears on a request card.
    expect(wrapper.text()).not.toContain("Family trip");
  });
});
