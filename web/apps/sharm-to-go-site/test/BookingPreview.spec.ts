import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import BookingPreviewPage from "../app/pages/booking-preview.vue";

function mountPage() {
  return mount(BookingPreviewPage, {
    global: {
      stubs: {
        NuxtLink: { template: "<a><slot /></a>" },
      },
    },
  });
}

function buttonWithText(wrapper: ReturnType<typeof mountPage>, label: string) {
  const button = wrapper.findAll("button").find(candidate => candidate.text().trim() === label);
  if (!button) throw new Error(`Button not found: ${label}`);
  return button;
}

describe("Sharm To Go booking design prototype", () => {
  it("updates the sample total as party and add-ons change", async () => {
    const wrapper = mountPage();

    expect(wrapper.text()).toContain("no live service, availability, booking or payment is created");
    expect(wrapper.text()).toContain("EGP 2,900");

    await wrapper.get('button[aria-label="Increase Adults"]').trigger("click");
    expect(wrapper.text()).toContain("EGP 4,350");

    await wrapper.get('input[type="checkbox"]').setValue(true);
    expect(wrapper.text()).toContain("EGP 4,700");
    expect(wrapper.text()).toContain("Design sample amount — not a public price");

    await buttonWithText(wrapper, "Add to cart · design only").trigger("click");
    expect(wrapper.text()).toContain("Nothing was saved to a real cart");
  });

  it("covers details and planned payment choices without creating a booking", async () => {
    const wrapper = mountPage();

    await buttonWithText(wrapper, "Continue to details").trigger("click");
    await flushPromises();

    await buttonWithText(wrapper, "Review payment methods").trigger("click");
    expect(wrapper.text()).toContain("Add a name and phone number");

    await wrapper.get('input[autocomplete="name"]').setValue("Design Customer");
    await wrapper.get('input[autocomplete="tel"]').setValue("+201000000000");
    await buttonWithText(wrapper, "Review payment methods").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Hosted Paymob checkout");
    expect(wrapper.text()).toContain("Fawry reference");
    expect(wrapper.text()).toContain("CIB is the proposed settlement account");

    await wrapper.findAll('input[type="checkbox"]').at(-1)!.setValue(true);
    await buttonWithText(wrapper, "Complete design preview").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("No booking or payment was created");
  });

  it("switches the complete booking prototype to Arabic RTL", async () => {
    const wrapper = mountPage();
    await buttonWithText(wrapper, "العربية").trigger("click");
    await flushPromises();

    expect(wrapper.get("main").attributes("dir")).toBe("rtl");
    expect(wrapper.text()).toContain("لا توجد خدمة أو مواعيد أو حجز أو عملية دفع فعلية");

    await buttonWithText(wrapper, "متابعة للبيانات").trigger("click");
    await wrapper.get('input[autocomplete="name"]').setValue("عميل تجربة");
    await wrapper.get('input[autocomplete="tel"]').setValue("+201000000000");
    await buttonWithText(wrapper, "مراجعة وسائل الدفع").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("كود فوري");
  });
});
