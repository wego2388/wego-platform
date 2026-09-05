import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import WegoInput from "../src/WegoInput.vue";

describe("WegoInput", () => {
  it("forwards disabled to the real <input>, not the wrapper (the real bug this fixes)", () => {
    const wrapper = mount(WegoInput, {
      props: { id: "code", label: "Code", modelValue: "", disabled: true },
    });
    expect(wrapper.get("input").attributes("disabled")).toBeDefined();
  });

  it("forwards placeholder to the real <input>", () => {
    const wrapper = mount(WegoInput, {
      props: { id: "amount", label: "Amount", modelValue: "" },
      attrs: { placeholder: "0.00" },
    });
    expect(wrapper.get("input").attributes("placeholder")).toBe("0.00");
  });

  it("forwards arbitrary native attributes (min/max/step) it never explicitly declared", () => {
    const wrapper = mount(WegoInput, {
      props: { id: "qty", label: "Quantity", modelValue: "1", type: "number" },
      attrs: { min: "1", max: "10", step: "1" },
    });
    const input = wrapper.get("input");
    expect(input.attributes("min")).toBe("1");
    expect(input.attributes("max")).toBe("10");
    expect(input.attributes("step")).toBe("1");
  });

  it("emits update:modelValue on input", async () => {
    const wrapper = mount(WegoInput, { props: { id: "title", label: "Title", modelValue: "" } });
    await wrapper.get("input").setValue("E2E Lifecycle Trip");
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual(["E2E Lifecycle Trip"]);
  });

  it("shows help text and links it via aria-describedby when there is no error", () => {
    const wrapper = mount(WegoInput, {
      props: { id: "email", label: "Email", modelValue: "", help: "We'll never share this." },
    });
    expect(wrapper.get("input").attributes("aria-describedby")).toBe("email-help");
    expect(wrapper.text()).toContain("We'll never share this.");
  });

  it("shows error text, marks aria-invalid, and prefers error over help when both are set", () => {
    const wrapper = mount(WegoInput, {
      props: { id: "email", label: "Email", modelValue: "", help: "help text", error: "Required" },
    });
    const input = wrapper.get("input");
    expect(input.attributes("aria-invalid")).toBe("true");
    expect(input.attributes("aria-describedby")).toBe("email-error");
    expect(wrapper.text()).toContain("Required");
    expect(wrapper.text()).not.toContain("help text");
  });
});
