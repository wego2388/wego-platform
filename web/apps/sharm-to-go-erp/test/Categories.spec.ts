import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import CategoriesPage from "../app/pages/categories.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleCategory = {
  id: "44444444-4444-4444-4444-444444444444",
  code: "sea-adventures",
  name: { en: "Sea adventures", ar: "مغامرات بحرية" },
  displayOrder: 0,
  status: "ACTIVE",
  createdAt: "2026-09-02T00:00:00Z",
};

describe("categories page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(CategoriesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists categories in both languages for an authenticated user with service:view", async () => {
    seedSession(["service:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleCategory]), { status: 200 })));

    const wrapper = mount(CategoriesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Sea adventures");
    expect(wrapper.text()).toContain("مغامرات بحرية");
    expect(wrapper.text()).toContain("sea-adventures");
  });

  it("hides the category form for a user without service:manage", async () => {
    seedSession(["service:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleCategory]), { status: 200 })));

    const wrapper = mount(CategoriesPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New category");
  });

  it("submits a new category and appends it to the list", async () => {
    seedSession(["service:view", "service:manage"]);
    const created = { ...sampleCategory, id: "new-category-id", code: "desert-safari", name: { en: "Desert Safari", ar: "سفاري" } };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/travel-marketplace/categories") {
        return new Response(JSON.stringify(created), { status: 201 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(CategoriesPage);
    await flushPromises();

    await wrapper.get("#code").setValue("desert-safari");
    await wrapper.get("#nameEn").setValue("Desert Safari");
    await wrapper.get("#nameAr").setValue("سفاري");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("Desert Safari");
  });

  it("disables the code field while editing, since the code is immutable after creation", async () => {
    seedSession(["service:view", "service:manage"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleCategory]), { status: 200 })));

    const wrapper = mount(CategoriesPage);
    await flushPromises();

    const editButton = wrapper.findAll("button").find((button) => button.text() === "Edit");
    await editButton?.trigger("click");
    await flushPromises();

    const codeField = wrapper.get("#code").element as HTMLInputElement;
    expect(codeField.value).toBe("sea-adventures");
    expect(codeField.disabled).toBe(true);
  });

  it("shows a real error, not a raw crash, when the server rejects a duplicate code", async () => {
    seedSession(["service:view", "service:manage"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/travel-marketplace/categories") {
        return new Response(JSON.stringify({ error: "duplicate_code" }), { status: 409 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(CategoriesPage);
    await flushPromises();

    await wrapper.get("#code").setValue("sea-adventures");
    await wrapper.get("#nameEn").setValue("Sea adventures");
    await wrapper.get("#nameAr").setValue("مغامرات بحرية");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toBe("A category with this code already exists.");
  });
});
