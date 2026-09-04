import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import WegoDialog from "../src/WegoDialog.vue";

describe("WegoDialog", () => {
  it("calls the native showModal() when open becomes true", async () => {
    const wrapper = mount(WegoDialog, { props: { open: false, title: "Close offering" } });
    const dialog = wrapper.get("dialog").element as HTMLDialogElement;
    expect(dialog.open).toBe(false);

    await wrapper.setProps({ open: true });
    expect(dialog.open).toBe(true);
  });

  it("calls the native close() when open becomes false", async () => {
    const wrapper = mount(WegoDialog, { props: { open: true, title: "Close offering" } });
    const dialog = wrapper.get("dialog").element as HTMLDialogElement;
    expect(dialog.open).toBe(true);

    await wrapper.setProps({ open: false });
    expect(dialog.open).toBe(false);
  });

  it("emits close when the native dialog fires its own close event (e.g. Escape)", async () => {
    const wrapper = mount(WegoDialog, { props: { open: true, title: "Close offering" } });
    const dialog = wrapper.get("dialog").element as HTMLDialogElement;

    dialog.dispatchEvent(new Event("close"));
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted("close")).toHaveLength(1);
  });

  it("labels the dialog via aria-labelledby pointing at the real, visible title", () => {
    const wrapper = mount(WegoDialog, { props: { open: true, title: "Close offering" } });
    const dialog = wrapper.get("dialog");
    const labelledBy = dialog.attributes("aria-labelledby");
    expect(labelledBy).toBeTruthy();
    expect(wrapper.get(`#${labelledBy}`).text()).toBe("Close offering");
  });

  it("renders body and actions slot content", () => {
    const wrapper = mount(WegoDialog, {
      props: { open: true, title: "Close offering" },
      slots: {
        default: "<p>This cannot be undone.</p>",
        actions: '<button type="button">Confirm</button>',
      },
    });
    expect(wrapper.html()).toContain("This cannot be undone.");
    expect(wrapper.get("button").text()).toBe("Confirm");
  });

  it("keeps native centering (m-auto) — Tailwind's preflight zeroes every element's margin, which otherwise defeats the UA stylesheet's own dialog auto-centering", () => {
    const wrapper = mount(WegoDialog, { props: { open: true, title: "Close offering" } });
    expect(wrapper.get("dialog").classes()).toContain("m-auto");
  });

  it("renders no actions region at all when the caller passes no actions slot", () => {
    const wrapper = mount(WegoDialog, { props: { open: true, title: "Info" } });
    expect(wrapper.findAll("button")).toHaveLength(0);
  });
});
