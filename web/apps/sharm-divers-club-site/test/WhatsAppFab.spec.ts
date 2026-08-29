import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import WhatsAppFab from "../app/components/WhatsAppFab.vue";

describe("WhatsAppFab", () => {
  it("links the real WhatsApp channel and is labelled for assistive tech", () => {
    const wrapper = mount(WhatsAppFab);
    const link = wrapper.get("a");

    expect(link.attributes("href")).toBe("https://wa.me/201066461010");
    expect(link.attributes("target")).toBe("_blank");
    expect(link.attributes("rel")).toBe("noopener");
    expect(link.attributes("aria-label")).toBeTruthy();
  });
});
