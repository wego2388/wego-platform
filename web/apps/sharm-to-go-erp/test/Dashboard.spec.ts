import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";

import DashboardPage from "../app/pages/index.vue";

describe("Sharm To Go operations foundation", () => {
  it("shows readiness without inventing live business totals", () => {
    const wrapper = mount(DashboardPage);

    expect(wrapper.text()).toContain("no live business data");
    expect(wrapper.text()).toContain("not deployed or connected yet");
    expect(wrapper.text()).toContain("No checkout, commissions, payouts or refunds are implemented");
    expect(wrapper.text()).not.toMatch(/revenue|bookings today|active providers/i);
  });

  it("switches the dashboard from English LTR to Arabic RTL", async () => {
    let headFactory: (() => { htmlAttrs: { dir: string; lang: string } }) | undefined;
    vi.stubGlobal("useHead", (input: typeof headFactory) => {
      headFactory = input;
    });
    const wrapper = mount(DashboardPage);
    expect(wrapper.get("main").attributes("dir")).toBe("ltr");
    expect(headFactory).toBeDefined();
    expect(headFactory!().htmlAttrs).toEqual({ dir: "ltr", lang: "en" });

    await wrapper.get("button").trigger("click");
    await flushPromises();

    expect(wrapper.get("main").attributes("dir")).toBe("rtl");
    expect(headFactory!().htmlAttrs).toEqual({ dir: "rtl", lang: "ar" });
    expect(wrapper.text()).toContain("لا توجد بيانات أعمال فعلية");
  });
});
