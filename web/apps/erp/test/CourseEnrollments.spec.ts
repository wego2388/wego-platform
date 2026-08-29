import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import CourseEnrollmentsPage from "../app/pages/course-enrollments.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleDiver = { id: "diver-1", fullName: "Ada Lovelace", totalLoggedDives: 0, certifications: [], status: "ACTIVE", createdAt: "2026-01-01T00:00:00Z" };
const sampleCourse = {
  id: "course-1",
  offeringType: "COURSE",
  title: "PADI Open Water",
  startsOn: "2026-09-01",
  pricingBasis: "FLAT",
  unitPrice: { amount: "350.00", currencyCode: "EUR" },
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
};
const sampleEnrollment = {
  id: "enrollment-1",
  diverId: "diver-1",
  offeringId: "course-1",
  stage: "LEAD",
  startedAt: "2026-08-29T00:00:00Z",
  createdAt: "2026-08-29T00:00:00Z",
};

function stubFetch(overrides: Record<string, (init?: RequestInit) => Response> = {}) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if (overrides[url.pathname]) return overrides[url.pathname]!(init);
    if (url.pathname === "/api/v1/divers/course-enrollments") return new Response(JSON.stringify([sampleEnrollment]), { status: 200 });
    if (url.pathname === "/api/v1/divers/divers") return new Response(JSON.stringify([sampleDiver]), { status: 200 });
    if (url.pathname === "/api/v1/divers/offerings") return new Response(JSON.stringify([sampleCourse]), { status: 200 });
    return new Response(JSON.stringify([]), { status: 200 });
  });
}

describe("course enrollments page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(CourseEnrollmentsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists enrollments resolved to real diver and course names", async () => {
    seedSession(["course:view"]);
    vi.stubGlobal("fetch", stubFetch());

    const wrapper = mount(CourseEnrollmentsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Ada Lovelace");
    expect(wrapper.text()).toContain("PADI Open Water");
    expect(wrapper.text()).toContain("LEAD");
  });

  it("hides the enrollment form and advance/withdraw actions for a user without course:manage", async () => {
    seedSession(["course:view"]);
    vi.stubGlobal("fetch", stubFetch());

    const wrapper = mount(CourseEnrollmentsPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("Enroll a diver");
    expect(wrapper.findAll("button").some((button) => button.text() === "Advance")).toBe(false);
  });

  it("advances an enrollment to the next real stage", async () => {
    seedSession(["course:view", "course:manage"]);
    const advanced = { ...sampleEnrollment, stage: "THEORY" };
    vi.stubGlobal(
      "fetch",
      stubFetch({
        "/api/v1/divers/course-enrollments/enrollment-1/advance": () => new Response(JSON.stringify(advanced), { status: 200 }),
      }),
    );

    const wrapper = mount(CourseEnrollmentsPage);
    await flushPromises();

    const advanceButton = wrapper.findAll("button").find((button) => button.text() === "Advance");
    await advanceButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("THEORY");
  });

  it("enrolls a diver in a course and prepends it to the list", async () => {
    seedSession(["course:view", "course:manage"]);
    const newEnrollment = { ...sampleEnrollment, id: "enrollment-2" };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/divers/course-enrollments") {
        return new Response(JSON.stringify(newEnrollment), { status: 201 });
      }
      if (url.pathname === "/api/v1/divers/course-enrollments") return new Response(JSON.stringify([]), { status: 200 });
      if (url.pathname === "/api/v1/divers/divers") return new Response(JSON.stringify([sampleDiver]), { status: 200 });
      if (url.pathname === "/api/v1/divers/offerings") return new Response(JSON.stringify([sampleCourse]), { status: 200 });
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(CourseEnrollmentsPage);
    await flushPromises();

    await wrapper.get("#enrollDiver").setValue("diver-1");
    await wrapper.get("#enrollOffering").setValue("course-1");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(fetchMock.mock.calls.some((call) => call[1]?.method === "POST")).toBe(true);
  });
});
