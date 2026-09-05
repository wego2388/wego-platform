import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import ServicesPage from "../app/pages/services.vue";
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

const sampleService = {
  id: "55555555-5555-5555-5555-555555555555",
  categoryId: sampleCategory.id,
  name: { en: "Desert Safari", ar: "سفاري صحراوي" },
  description: { en: "An evening safari.", ar: "رحلة مسائية." },
  fulfilmentModel: "DIRECT",
  confirmationType: "INSTANT",
  cancellationPolicy: { en: "Free cancellation.", ar: "إلغاء مجاني." },
  options: [
    {
      id: "opt-1",
      label: { en: "Evening trip", ar: "رحلة مسائية" },
      maxParticipants: 10,
      priceAmount: "500.00",
      priceCurrency: "EGP",
      priceBasis: "PER_PERSON",
    },
  ],
  media: [{ id: "media-1", assetReference: "asset-1", rightsEvidence: "Owner-supplied", locale: "en" }],
  status: "DRAFT",
  createdAt: "2026-09-02T00:00:00Z",
};

function fetchRoutedBy(routes: Record<string, () => Response>, fallback: () => Response) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    const key = `${init?.method ?? "GET"} ${url.pathname}`;
    return (routes[key] ?? fallback)();
  });
}

describe("services page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(ServicesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists services with category, option, and media counts for an authenticated user", async () => {
    seedSession(["service:view"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          "GET /api/v1/travel-marketplace/services": () => new Response(JSON.stringify([sampleService]), { status: 200 }),
          "GET /api/v1/travel-marketplace/categories": () => new Response(JSON.stringify([sampleCategory]), { status: 200 }),
          "GET /api/v1/travel-marketplace/providers": () => new Response(JSON.stringify([]), { status: 200 }),
        },
        () => new Response(JSON.stringify([]), { status: 200 }),
      ),
    );

    const wrapper = mount(ServicesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Desert Safari");
    expect(wrapper.text()).toContain("Sea adventures");
    expect(wrapper.text()).toContain("1 option(s)");
    expect(wrapper.text()).toContain("1 photo(s)");
    expect(wrapper.text()).toContain("DRAFT");
  });

  it("hides the service form for a user without service:manage", async () => {
    seedSession(["service:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([]), { status: 200 })));

    const wrapper = mount(ServicesPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New service");
  });

  it("only offers the transitions valid from the service's current status", async () => {
    seedSession(["service:view", "service:manage"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          "GET /api/v1/travel-marketplace/services": () => new Response(JSON.stringify([sampleService]), { status: 200 }),
        },
        () => new Response(JSON.stringify([]), { status: 200 }),
      ),
    );

    const wrapper = mount(ServicesPage);
    await flushPromises();

    const buttonLabels = wrapper.findAll("button").map((button) => button.text());
    expect(buttonLabels).toContain("Submit for review");
    expect(buttonLabels).toContain("archive");
    expect(buttonLabels).not.toContain("publish");
    expect(buttonLabels).not.toContain("approve");
  });

  it("advances a service through submit-for-review and shows the new status", async () => {
    seedSession(["service:view", "service:manage"]);
    let currentStatus = "DRAFT";
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          "GET /api/v1/travel-marketplace/services": () =>
            new Response(JSON.stringify([{ ...sampleService, status: currentStatus }]), { status: 200 }),
          "POST /api/v1/travel-marketplace/services/55555555-5555-5555-5555-555555555555/submit-for-review": () => {
            currentStatus = "REVIEW";
            return new Response(JSON.stringify({ ...sampleService, status: "REVIEW" }), { status: 200 });
          },
        },
        () => new Response(JSON.stringify([]), { status: 200 }),
      ),
    );

    const wrapper = mount(ServicesPage);
    await flushPromises();

    const submitButton = wrapper.findAll("button").find((button) => button.text() === "Submit for review");
    await submitButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("REVIEW");
  });

  it("shows a real error, not a raw crash, when publishing is rejected for missing content", async () => {
    seedSession(["service:view", "service:manage"]);
    const approvedService = { ...sampleService, status: "APPROVED" };
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          "GET /api/v1/travel-marketplace/services": () => new Response(JSON.stringify([approvedService]), { status: 200 }),
          "POST /api/v1/travel-marketplace/services/55555555-5555-5555-5555-555555555555/publish": () =>
            new Response(JSON.stringify({ error: "missing_publishable_option" }), { status: 409 }),
        },
        () => new Response(JSON.stringify([]), { status: 200 }),
      ),
    );

    const wrapper = mount(ServicesPage);
    await flushPromises();

    const publishButton = wrapper.findAll("button").find((button) => button.text() === "publish");
    await publishButton?.trigger("click");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toBe("Add at least one option before publishing.");
  });

  it("submits a new DIRECT service with one option and one media asset", async () => {
    seedSession(["service:view", "service:manage"]);
    const created = { ...sampleService, id: "new-service-id", name: { en: "New Service", ar: "خدمة جديدة" } };
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          "GET /api/v1/travel-marketplace/categories": () => new Response(JSON.stringify([sampleCategory]), { status: 200 }),
          "POST /api/v1/travel-marketplace/services": () => new Response(JSON.stringify(created), { status: 201 }),
        },
        () => new Response(JSON.stringify([]), { status: 200 }),
      ),
    );

    const wrapper = mount(ServicesPage);
    await flushPromises();

    await wrapper.get("#categoryId").setValue(sampleCategory.id);
    await wrapper.get("#nameEn").setValue("New Service");
    await wrapper.get("#nameAr").setValue("خدمة جديدة");
    await wrapper.get("#descriptionEn").setValue("A new service.");
    await wrapper.get("#descriptionAr").setValue("خدمة جديدة.");
    await wrapper.get("#cancellationPolicyEn").setValue("Free cancellation.");
    await wrapper.get("#cancellationPolicyAr").setValue("إلغاء مجاني.");
    await wrapper.get("#opt-label-en-0").setValue("Standard");
    await wrapper.get("#opt-label-ar-0").setValue("قياسي");
    await wrapper.get("#media-ref-0").setValue("asset-001");
    await wrapper.get("#media-rights-0").setValue("Owner-supplied");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("New Service");
  });
});
