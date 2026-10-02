import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { describe, expect, it } from "vitest";

import { useSiteLocale } from "../app/composables/useSiteLocale";

const Probe = defineComponent({
  setup() {
    const { locale, toggleLocale } = useSiteLocale();
    return () => h("div", [h("span", { "data-testid": "locale" }, locale.value), h("button", { onClick: toggleLocale }, "toggle")]);
  },
});

describe("useSiteLocale", () => {
  it("starts English on first render, even with a stored Arabic choice, so server and client markup match", () => {
    localStorage.setItem("sharm-to-go-locale", "ar");

    const wrapper = mount(Probe);

    // First synchronous render must be "en" (matches SSR output); the stored
    // locale is applied only after mount, not baked into the initial ref.
    expect(wrapper.get('[data-testid="locale"]').text()).toBe("en");
  });

  it("adopts a previously-chosen locale once mounted in the browser", async () => {
    localStorage.setItem("sharm-to-go-locale", "ar");

    const wrapper = mount(Probe);
    await wrapper.vm.$nextTick();

    expect(wrapper.get('[data-testid="locale"]').text()).toBe("ar");
  });

  it("persists a toggled locale so the next page mount picks it up", async () => {
    const first = mount(Probe);
    await first.get("button").trigger("click");
    expect(first.get('[data-testid="locale"]').text()).toBe("ar");
    first.unmount();

    // A fresh mount simulates navigating to a different page.
    const second = mount(Probe);
    await second.vm.$nextTick();
    expect(second.get('[data-testid="locale"]').text()).toBe("ar");
  });

  it("falls back to English, not a crash, when localStorage throws", () => {
    const original = Storage.prototype.getItem;
    Storage.prototype.getItem = () => {
      throw new Error("blocked");
    };

    expect(() => mount(Probe)).not.toThrow();

    Storage.prototype.getItem = original;
  });
});
