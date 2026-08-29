import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import ConditionsWidget from "../app/components/ConditionsWidget.vue";

const props = {
  locale: "en" as const,
  heading: "Live conditions",
  loadingLabel: "Checking live conditions…",
  unavailableLabel: "Live conditions unavailable",
  airLabel: "Air",
  seaLabel: "Sea",
  windLabel: "Wind",
  waveLabel: "Wave height",
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ConditionsWidget", () => {
  it("shows real live values once the conditions request resolves", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(
          JSON.stringify({
            fetchedAt: "2026-08-29T19:30:00Z",
            air: { tempC: 37.4, windKph: 16.7, weatherCode: 0 },
            sea: { tempC: 29.0, waveHeightM: 0.56 },
          }),
          { status: 200 },
        ),
      ),
    );

    const wrapper = mount(ConditionsWidget, { props });
    await flushPromises();

    expect(wrapper.text()).toContain("37°C");
    expect(wrapper.text()).toContain("29°C");
    expect(wrapper.text()).toContain("17 km/h");
    expect(wrapper.text()).toContain("0.6 m");
    expect(wrapper.text()).toContain("Clear sky");
  });

  it("shows an honest unavailable message, never a fabricated value, when the request fails", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 500 })));

    const wrapper = mount(ConditionsWidget, { props });
    await flushPromises();

    expect(wrapper.text()).toContain("Live conditions unavailable");
    expect(wrapper.text()).not.toContain("°C");
  });
});
