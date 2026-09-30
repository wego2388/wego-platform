import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import RequestPage from "../app/pages/experiences/[id]/request.vue";

afterEach(() => {
  vi.unstubAllGlobals();
});

const serviceId = "55555555-5555-5555-5555-555555555555";
const optionId = "66666666-6666-6666-6666-666666666666";

function sampleService(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: serviceId,
    categoryId: "44444444-4444-4444-4444-444444444444",
    name: { en: "Desert Safari", ar: "سفاري صحراوي" },
    description: { en: "An evening safari.", ar: "رحلة مسائية." },
    confirmationType: "INSTANT",
    cancellationPolicy: { en: "Free cancellation up to 24h before.", ar: "إلغاء مجاني قبل ٢٤ ساعة." },
    pickupInfo: null,
    inclusions: null,
    exclusions: null,
    operatedBy: null,
    options: [
      { id: optionId, label: { en: "Evening trip", ar: "رحلة مسائية" }, durationMinutes: 180, maxParticipants: 3, priceAmount: "500.00", priceCurrency: "EGP", priceBasis: "PER_PERSON" },
    ],
    media: [],
    ...overrides,
  };
}

function withRoute(id: string, query: Record<string, string> = {}) {
  vi.stubGlobal("useRoute", () => ({ params: { id }, query }));
}

function mountPage() {
  return mount(RequestPage, {
    global: { stubs: { NuxtLink: { template: "<a :href=\"to\"><slot /></a>", props: ["to"] } } },
  });
}

async function fillPartyStep(wrapper: ReturnType<typeof mountPage>, date = "2099-01-01") {
  await wrapper.get("#date").setValue(date);
  await wrapper.get("form").trigger("submit");
  await flushPromises();
}

async function fillContactStep(wrapper: ReturnType<typeof mountPage>) {
  await wrapper.get("#name").setValue("Nour");
  await wrapper.get("#phone").setValue("+201001413469");
  await wrapper.get("form").trigger("submit");
  await flushPromises();
}

describe("real request flow", () => {
  it("loads the service and walks through party -> contact -> review -> instant success", async () => {
    withRoute(serviceId);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (url.pathname === `/api/catalog/services/${serviceId}`) {
        return new Response(JSON.stringify(sampleService()), { status: 200 });
      }
      if (url.pathname === "/api/requests" && init?.method === "POST") {
        return new Response(
          JSON.stringify({
            reference: "STG-ABCDEFGH",
            status: "CONFIRMED",
            serviceName: { en: "Desert Safari", ar: "سفاري صحراوي" },
            optionLabel: { en: "Evening trip", ar: "رحلة مسائية" },
            priceAmount: "500.00",
            priceCurrency: "EGP",
            priceBasis: "PER_PERSON",
            cancellationPolicy: { en: "Free cancellation.", ar: "إلغاء مجاني." },
            requestedDate: "2099-01-01",
            adults: 2,
            children: 0,
            createdAt: "2026-10-01T00:00:00Z",
            confirmedAt: "2026-10-01T00:00:01Z",
          }),
          { status: 201 },
        );
      }
      throw new Error(`Unexpected fetch: ${url.pathname}`);
    });
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("navigator", { clipboard: { writeText: vi.fn() } });

    const wrapper = mountPage();
    await flushPromises();
    expect(wrapper.text()).toContain("Desert Safari");

    await fillPartyStep(wrapper);
    expect(wrapper.text()).toContain("How should we reach you?");

    await fillContactStep(wrapper);
    expect(wrapper.text()).toContain("Review your request");
    expect(wrapper.text()).toContain("EGP 500.00");

    await wrapper.get('button[type="button"].flex-1').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Confirmed!");
    expect(wrapper.text()).toContain("STG-ABCDEFGH");

    const submitCall = fetchMock.mock.calls.find((call) => String(call[0]).includes("/api/requests"));
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string);
    expect(body.serviceOptionId).toBe(optionId);
    expect(body.customer).toEqual({ name: "Nour", phone: "+201001413469", email: undefined });
    expect((submitCall?.[1] as RequestInit).headers).toHaveProperty("Idempotency-Key");
  });

  it("shows the awaiting-review heading, not a false instant-confirmed one, for a STAFF_REVIEW result", async () => {
    withRoute(serviceId);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(String(input), "http://localhost");
        if (url.pathname === `/api/catalog/services/${serviceId}`) {
          return new Response(JSON.stringify(sampleService({ confirmationType: "STAFF_REVIEW" })), { status: 200 });
        }
        if (url.pathname === "/api/requests" && init?.method === "POST") {
          return new Response(
            JSON.stringify({
              reference: "STG-ZZZZZZZZ",
              status: "NEW",
              serviceName: { en: "Desert Safari", ar: "سفاري صحراوي" },
              optionLabel: { en: "Evening trip", ar: "رحلة مسائية" },
              priceAmount: "500.00",
              priceCurrency: "EGP",
              priceBasis: "PER_PERSON",
              cancellationPolicy: { en: "Free cancellation.", ar: "إلغاء مجاني." },
              requestedDate: "2099-01-01",
              adults: 2,
              children: 0,
              createdAt: "2026-10-01T00:00:00Z",
            }),
            { status: 201 },
          );
        }
        throw new Error(`Unexpected fetch: ${url.pathname}`);
      }),
    );
    vi.stubGlobal("navigator", { clipboard: { writeText: vi.fn() } });

    const wrapper = mountPage();
    await flushPromises();
    await fillPartyStep(wrapper);
    await fillContactStep(wrapper);
    await wrapper.get('button[type="button"].flex-1').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Request received");
    expect(wrapper.text()).not.toContain("Confirmed!");
  });

  it("blocks continuing past the party step when the party exceeds the option's capacity", async () => {
    withRoute(serviceId);
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(sampleService()), { status: 200 })),
    );

    const wrapper = mountPage();
    await flushPromises();

    // The sample option's maxParticipants is 3; push adults to 3 then try to add one more guest via children.
    const adultsIncrease = wrapper.get('button[aria-label="Continue Adults"]');
    await adultsIncrease.trigger("click");
    const childrenIncrease = wrapper.get('button[aria-label="Continue Children"]');
    await childrenIncrease.trigger("click");
    await childrenIncrease.trigger("click");

    await wrapper.get("#date").setValue("2099-01-01");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("fits up to 3 people");
    expect(wrapper.text()).not.toContain("How should we reach you?");
  });

  it("blocks continuing past the contact step without a name and at least one contact method", async () => {
    withRoute(serviceId);
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(sampleService()), { status: 200 })),
    );

    const wrapper = mountPage();
    await flushPromises();
    await fillPartyStep(wrapper);

    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("Please add your name and at least one way to reach you");
    expect(wrapper.text()).not.toContain("Review your request");
  });

  it("maps a backend 409 (party size exceeds capacity) to the honest error message, not a raw one", async () => {
    withRoute(serviceId);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(String(input), "http://localhost");
        if (url.pathname === `/api/catalog/services/${serviceId}`) {
          return new Response(JSON.stringify(sampleService()), { status: 200 });
        }
        if (url.pathname === "/api/requests" && init?.method === "POST") {
          return new Response(JSON.stringify({ error: "party_size_exceeds_capacity" }), { status: 409 });
        }
        throw new Error(`Unexpected fetch: ${url.pathname}`);
      }),
    );

    const wrapper = mountPage();
    await flushPromises();
    await fillPartyStep(wrapper);
    await fillContactStep(wrapper);
    await wrapper.get('button[type="button"].flex-1').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Your party is larger than this option allows");
  });

  it("pre-fills the party step from a date/adults/children carried over from the homepage search box", async () => {
    withRoute(serviceId, { date: "2099-06-15", adults: "3", children: "1" });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(sampleService()), { status: 200 })),
    );

    const wrapper = mountPage();
    await flushPromises();

    expect((wrapper.get("#date").element as HTMLInputElement).value).toBe("2099-06-15");
    // adults(3) + children(1) = 4, one more than the sample option's maxParticipants(3) —
    // proves the pre-fill does not silently bypass the real capacity check.
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.text()).toContain("fits up to 3 people");
  });

  it("ignores a past date carried over in the query instead of silently accepting it", async () => {
    withRoute(serviceId, { date: "2020-01-01" });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(sampleService()), { status: 200 })),
    );

    const wrapper = mountPage();
    await flushPromises();

    expect((wrapper.get("#date").element as HTMLInputElement).value).toBe("");
  });

  it("shows an honest not-found state for an unknown or unpublished service id", async () => {
    withRoute("00000000-0000-0000-0000-000000000000");
    vi.stubGlobal("fetch", vi.fn(async () => new Response("not found", { status: 404 })));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("This experience isn't available");
  });
});
