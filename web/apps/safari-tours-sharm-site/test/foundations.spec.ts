import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import { defineComponent, h, ref } from "vue";
import Button from "../app/components/ui/Button.vue";
import Field from "../app/components/ui/Field.vue";
import Stepper from "../app/components/ui/Stepper.vue";
import ErrorSummary from "../app/components/ui/ErrorSummary.vue";
import TourMedia from "../app/components/brand/TourMedia.vue";
import { localeRedirectTarget } from "../server/utils/localeRedirect";

// Nuxt auto-registered components are stubbed in unit tests.
const stubs = {
  Icon: { props: ["name"], template: "<i :data-icon='name' />" },
  NuxtLinkLocale: { props: ["to"], template: "<a :data-to='to'><slot /></a>" },
  NuxtImg: { props: ["src", "alt"], template: "<img :src='src' :alt='alt'>" },
};

describe("language-less URL redirect", () => {
  it("keeps the payment return URL working, query included", () => {
    expect(localeRedirectTarget("/booking/payment-result", "?provider=mock&id=1", undefined))
      .toBe("/en/booking/payment-result?provider=mock&id=1");
  });
  it("uses the remembered language and ignores unknown ones", () => {
    expect(localeRedirectTarget("/tours", "", "ar")).toBe("/ar/tours");
    expect(localeRedirectTarget("/tours", "", "de")).toBe("/en/tours");
  });
  it("leaves localized pages, the root, assets, APIs and unknown paths alone", () => {
    for (const path of ["/", "/ru/tours", "/_safari/app.js", "/api/v1/x", "/media/tours/a/b.avif", "/favicon.svg", "/brand/logo.webp", "/wp-admin", "/foo"]) {
      expect(localeRedirectTarget(path, "", "ar")).toBeNull();
    }
  });
  it("normalises a wrongly-cased locale", () => {
    expect(localeRedirectTarget("/EN/tours", "?a=1", undefined)).toBe("/en/tours?a=1");
  });
  it("can never produce an off-site (protocol-relative) redirect", () => {
    for (const path of ["//evil.com", "/\\evil.com", "//evil.com/tours"]) {
      const target = localeRedirectTarget(path, "", "en");
      expect(target === null || /^\/(en|ar|ru|it)\//.test(target)).toBe(true);
    }
  });
});

describe("Button", () => {
  it("renders a button that is busy and disabled while loading", () => {
    const wrapper = mount(Button, { props: { loading: true }, slots: { default: "Pay" }, global: { stubs } });
    const button = wrapper.get("button");
    expect(button.attributes("disabled")).toBeDefined();
    expect(button.attributes("aria-busy")).toBe("true");
  });
  it("renders a locale-aware link for internal destinations", () => {
    const wrapper = mount(Button, { props: { to: "/tours" }, slots: { default: "Tours" }, global: { stubs } });
    expect(wrapper.get("a").attributes("data-to")).toBe("/tours");
  });
  it("never renders a live link when disabled", () => {
    const wrapper = mount(Button, { props: { to: "/tours", disabled: true }, slots: { default: "Tours" }, global: { stubs } });
    expect(wrapper.find("a").exists()).toBe(false);
    expect(wrapper.get("button").attributes("disabled")).toBeDefined();
  });
});

describe("Field", () => {
  it("connects label, hint and error to the control", () => {
    const wrapper = mount(Field, {
      props: { label: "Full name", hint: "As on your passport", error: "Required", id: "name", required: true },
      slots: {
        default: (slot: { id: string; describedBy?: string; invalid: boolean }) =>
          h("input", { id: slot.id, "aria-describedby": slot.describedBy, "aria-invalid": slot.invalid }),
      },
      global: { stubs },
    });
    expect(wrapper.get("label").attributes("for")).toBe("name");
    expect(wrapper.get("input").attributes("aria-describedby")).toBe("name-hint name-error");
    // Inline errors are read through aria-describedby; the form's error
    // summary is the single live announcement (no duplicate alerts).
    expect(wrapper.get("#name-error").text()).toContain("Required");
    expect(wrapper.find("[role=alert]").exists()).toBe(false);
  });
});

describe("Stepper", () => {
  it("stays within its bounds", async () => {
    const Host = defineComponent({
      components: { Stepper },
      setup: () => ({ count: ref(1) }),
      template: `<Stepper v-model="count" label="Adults" :min="1" :max="2" />`,
    });
    const wrapper = mount(Host, { global: { stubs } });
    const [minus, plus] = wrapper.findAll("button");
    expect(minus!.attributes("disabled")).toBeDefined();
    await plus!.trigger("click");
    expect(wrapper.get("output").text()).toBe("2");
    expect(plus!.attributes("disabled")).toBeDefined();
  });
});

describe("ErrorSummary", () => {
  it("links every error to its field", () => {
    const wrapper = mount(ErrorSummary, {
      props: { title: "Fix these", errors: [{ fieldId: "name", message: "Enter your name" }] },
    });
    expect(wrapper.get("a").attributes("href")).toBe("#name");
  });
});

describe("TourMedia", () => {
  it("shows a labelled branded placeholder until a real photo exists", () => {
    const wrapper = mount(TourMedia, { props: { alt: "Quad bikes at sunset", category: "DESERT" }, global: { stubs } });
    expect(wrapper.find("img").exists()).toBe(false);
    expect(wrapper.get("[role=img]").attributes("aria-label")).toBe("Quad bikes at sunset");
  });
  it("renders the approved photo with its alt text", () => {
    const wrapper = mount(TourMedia, {
      props: { src: "/media/tours/super-safari/cover.avif", alt: "Quad bikes", category: "DESERT" },
      global: { stubs },
    });
    expect(wrapper.get("img").attributes("alt")).toBe("Quad bikes");
  });
  it("renders managed photos natively with actual dimensions and safe derivatives", () => {
    const src = "/media/tours/super-safari/12345678-1234-1234-1234-123456789abc.jpg";
    const wrapper = mount(TourMedia, {
      props: { src, alt: "Approved local photo", category: "DESERT", width: 600, height: 400 },
      global: { stubs },
    });
    const image = wrapper.get("img");
    expect(image.attributes()).toMatchObject({ src, width: "600", height: "400", decoding: "async", loading: "lazy" });
    expect(image.attributes("srcset")).toBe(`${src}?v=w360 360w, ${src} 600w`);
    expect(wrapper.findComponent({ name: "NuxtImg" }).exists()).toBe(false);
  });
});
