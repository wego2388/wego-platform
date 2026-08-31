import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import AttendancePage from "../app/pages/attendance.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleEmployee = {
  id: "44444444-4444-4444-4444-444444444444",
  fullName: "Youssef Adel",
  position: "Front Desk",
  status: "ACTIVE",
};

const sampleRecord = {
  id: "55555555-5555-5555-5555-555555555555",
  employeeId: sampleEmployee.id,
  attendanceDate: "2026-08-30",
  status: "PRESENT",
  notes: "On time",
  createdAt: "2026-08-30T00:00:00Z",
  updatedAt: "2026-08-30T00:00:00Z",
};

function fetchMockFor(records: unknown[] = [sampleRecord]) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if (init?.method === "POST" && url.pathname === "/api/v1/hr/attendance") {
      return new Response(JSON.stringify(sampleRecord), { status: 200 });
    }
    if (url.pathname === "/api/v1/hr/employees") {
      return new Response(JSON.stringify([sampleEmployee]), { status: 200 });
    }
    if (url.pathname === "/api/v1/hr/attendance") {
      return new Response(JSON.stringify(records), { status: 200 });
    }
    return new Response(JSON.stringify([]), { status: 200 });
  });
}

describe("attendance page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(AttendancePage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists attendance records with the employee's name resolved from the roster", async () => {
    seedSession(["hr:attendance-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(AttendancePage);
    await flushPromises();

    expect(wrapper.text()).toContain("Youssef Adel");
    expect(wrapper.text()).toContain("PRESENT");
    expect(wrapper.text()).toContain("On time");
  });

  it("hides the record form for a user without hr:attendance-manage", async () => {
    seedSession(["hr:attendance-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(AttendancePage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("Record attendance");
  });

  it("records attendance and shows it in the list", async () => {
    seedSession(["hr:attendance-view", "hr:attendance-manage"]);
    vi.stubGlobal("fetch", fetchMockFor([]));

    const wrapper = mount(AttendancePage);
    await flushPromises();

    await wrapper.get("#employeeId").setValue(sampleEmployee.id);
    await wrapper.get("#attendanceDate").setValue("2026-08-30");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    // "Youssef Adel" also appears in the form's own employee dropdown — the
    // notes text only appears once the recorded row renders as a card.
    expect(wrapper.text()).toContain("On time");
  });
});
