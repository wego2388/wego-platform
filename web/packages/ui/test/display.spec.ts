import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import WegoAlert from "../src/WegoAlert.vue";
import WegoBadge from "../src/WegoBadge.vue";
import WegoButton from "../src/WegoButton.vue";
import WegoEmptyState from "../src/WegoEmptyState.vue";
import WegoPageHeader from "../src/WegoPageHeader.vue";
import WegoPagination from "../src/WegoPagination.vue";
import WegoPanel from "../src/WegoPanel.vue";

describe("WegoBadge", () => {
  it.each(["neutral", "accent", "success", "warning", "danger", "info"] as const)(
    "renders the %s tone with its slot content, never relying on color alone",
    (tone: "neutral" | "accent" | "success" | "warning" | "danger" | "info") => {
      const wrapper = mount(WegoBadge, { props: { tone }, slots: { default: "ACTIVE" } });
      expect(wrapper.text()).toBe("ACTIVE");
    },
  );
});

describe("WegoAlert", () => {
  it("supports the new info variant alongside the existing three", () => {
    const wrapper = mount(WegoAlert, { props: { variant: "info" }, slots: { default: "A heads up." } });
    expect(wrapper.text()).toBe("A heads up.");
    expect(wrapper.attributes("role")).toBe("alert");
  });

  it("is a live region so a validation error is announced without extra wiring", () => {
    const wrapper = mount(WegoAlert, { props: { variant: "danger" }, slots: { default: "Failed." } });
    expect(wrapper.attributes("aria-live")).toBe("polite");
  });
});

describe("WegoButton", () => {
  it.each(["primary", "secondary", "destructive", "ghost"] as const)(
    "renders the %s variant",
    (variant: "primary" | "secondary" | "destructive" | "ghost") => {
    const wrapper = mount(WegoButton, { props: { variant }, slots: { default: "Go" } });
    expect(wrapper.text()).toBe("Go");
  });

  it("disables the button and marks aria-busy while loading", () => {
    const wrapper = mount(WegoButton, { props: { loading: true }, slots: { default: "Saving" } });
    expect(wrapper.get("button").attributes("disabled")).toBeDefined();
    expect(wrapper.get("button").attributes("aria-busy")).toBe("true");
  });
});

describe("WegoPanel", () => {
  it("renders title, description, and default slot content", () => {
    const wrapper = mount(WegoPanel, {
      props: { title: "Trial Balance", description: "As of a date." },
      slots: { default: "<p>Body</p>" },
    });
    expect(wrapper.get("h2").text()).toBe("Trial Balance");
    expect(wrapper.text()).toContain("As of a date.");
    expect(wrapper.html()).toContain("Body");
  });

  it("renders no empty header at all when no title/description/actions are given", () => {
    const wrapper = mount(WegoPanel, { slots: { default: "<p>Just content</p>" } });
    expect(wrapper.find("header").exists()).toBe(false);
  });
});

describe("WegoPageHeader", () => {
  it("renders the eyebrow, an <h1> title, and an optional description", () => {
    const wrapper = mount(WegoPageHeader, { props: { title: "Financial Reports", description: "Real numbers." } });
    expect(wrapper.text()).toContain("Wego Platform");
    expect(wrapper.get("h1").text()).toBe("Financial Reports");
    expect(wrapper.text()).toContain("Real numbers.");
  });
});

describe("WegoEmptyState", () => {
  it("renders the given message", () => {
    const wrapper = mount(WegoEmptyState, { props: { message: "Nothing scheduled." } });
    expect(wrapper.text()).toBe("Nothing scheduled.");
  });
});

describe("WegoPagination", () => {
  it("disables Previous on the first page and enables Next when there's more", () => {
    const wrapper = mount(WegoPagination, { props: { page: 0, hasNextPage: true } });
    const buttons = wrapper.findAll("button");
    expect(buttons[0]?.attributes("disabled")).toBeDefined();
    expect(buttons[1]?.attributes("disabled")).toBeUndefined();
    expect(wrapper.text()).toContain("Page 1");
  });

  it("disables Next on a short final page and emits previous/next", async () => {
    const wrapper = mount(WegoPagination, { props: { page: 2, hasNextPage: false } });
    expect(wrapper.text()).toContain("Page 3");
    const buttons = wrapper.findAll("button");
    expect(buttons[1]?.attributes("disabled")).toBeDefined();
    await buttons[0]?.trigger("click");
    expect(wrapper.emitted("previous")).toHaveLength(1);
  });
});
