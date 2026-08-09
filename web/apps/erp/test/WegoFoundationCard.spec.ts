import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import { WegoFoundationCard } from "@wego/ui";

describe("WegoFoundationCard", () => {
  it("exposes its semantic state and content", () => {
    const wrapper = mount(WegoFoundationCard, {
      props: {
        title: "Secure by default",
        state: "ready",
        description: "Business routes are denied until authorized.",
      },
    });

    expect(wrapper.get("article").attributes("data-state")).toBe("ready");
    expect(wrapper.get("h3").text()).toBe("Secure by default");
    expect(wrapper.text()).toContain("Business routes are denied until authorized.");
  });
});
