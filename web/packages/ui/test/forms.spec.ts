import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import WegoCheckbox from "../src/WegoCheckbox.vue";
import WegoSelect from "../src/WegoSelect.vue";
import WegoTextarea from "../src/WegoTextarea.vue";

describe("WegoSelect", () => {
  it("emits update:modelValue on change and renders slotted options", async () => {
    const wrapper = mount(WegoSelect, {
      props: { id: "type", label: "Type", modelValue: "DIVE_TRIP" },
      slots: {
        default: '<option value="DIVE_TRIP">Dive trip</option><option value="COURSE">Course</option>',
      },
    });
    expect(wrapper.findAll("option")).toHaveLength(2);
    await wrapper.get("select").setValue("COURSE");
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual(["COURSE"]);
  });

  it("forwards disabled to the real <select>", () => {
    const wrapper = mount(WegoSelect, {
      props: { id: "type", label: "Type", modelValue: "" },
      attrs: { disabled: true },
    });
    expect(wrapper.get("select").attributes("disabled")).toBeDefined();
  });
});

describe("WegoTextarea", () => {
  it("emits update:modelValue on input", async () => {
    const wrapper = mount(WegoTextarea, { props: { id: "notes", label: "Notes", modelValue: "" } });
    await wrapper.get("textarea").setValue("A real note.");
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual(["A real note."]);
  });

  it("forwards arbitrary attributes like maxlength", () => {
    const wrapper = mount(WegoTextarea, {
      props: { id: "notes", label: "Notes", modelValue: "" },
      attrs: { maxlength: "500" },
    });
    expect(wrapper.get("textarea").attributes("maxlength")).toBe("500");
  });
});

describe("WegoCheckbox", () => {
  it("reflects modelValue as checked and emits the new value on change", async () => {
    const wrapper = mount(WegoCheckbox, {
      props: { id: "perm-diver-view", modelValue: false },
      slots: { default: "diver:view" },
    });
    expect((wrapper.get("input").element as HTMLInputElement).checked).toBe(false);
    await wrapper.get("input").setValue(true);
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([true]);
  });

  it("associates its label with the input via a real <label for>, not just visual proximity", () => {
    const wrapper = mount(WegoCheckbox, {
      props: { id: "perm-diver-view", modelValue: false },
      slots: { default: "diver:view" },
    });
    expect(wrapper.get("label").attributes("for")).toBe("perm-diver-view");
  });
});
