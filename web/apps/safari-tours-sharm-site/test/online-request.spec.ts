import { flushPromises, mount } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import TourRequestForm from "../app/components/tour/TourRequestForm.vue";
import { onlineRequestCopy } from "../app/content/onlineRequest";
import { PublicApiError, submitOnlineRequest } from "../app/composables/usePublicToursApi";

const state = vi.hoisted(() => ({ locale: "en" as "en" | "ar" | "ru" | "it", track: vi.fn() }));
vi.mock("../app/composables/useSiteLocale", () => ({ useSiteLocale: () => ref(state.locale) }));
vi.mock("../app/composables/useAnalytics", () => ({ useAnalytics: () => ({ track: state.track }) }));
vi.mock("../app/composables/usePublicToursApi", async () => ({ ...await vi.importActual("../app/composables/usePublicToursApi"), submitOnlineRequest: vi.fn() }));
const selection = { tourId: "00000000-0000-4000-8000-000000000001", preferredDate: "2027-01-01", adultsCount: 2, childrenCount: 0 };
function make(valid = true) {
  vi.stubGlobal("useLocalePath", () => (path: string) => `/${state.locale}${path}`);
  return mount(TourRequestForm, { props: { selection, valid }, global: { stubs: {
    UiButton: { props: ["disabled", "type"], template: '<button :disabled="disabled" :type="type"><slot /></button>' },
    NuxtLink: { props: ["to"], template: '<a :href="to"><slot /></a>' },
  } } });
}
async function fill(wrapper: ReturnType<typeof make>) {
  for (const [name, value] of Object.entries({ fullName: "Test Guest", phone: "+201000000001", nationality: "EG", hotelName: "Test Hotel", email: "guest@example.com" })) await wrapper.find(`[name="${name}"]`).setValue(value);
  await wrapper.find('[name="privacy"]').setValue(true);
}
afterEach(() => { state.locale = "en"; vi.clearAllMocks(); vi.unstubAllGlobals(); });
describe("website request is saved here, never handed off to WhatsApp", () => {
  for (const locale of ["en", "ar", "ru", "it"] as const) it(`${locale}: truthful confirmation and separate privacy link`, async () => {
    state.locale = locale;
    vi.mocked(submitOnlineRequest).mockResolvedValue({ reference: `STQ-${"A".repeat(32)}` });
    const wrapper = make(); await fill(wrapper); await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(wrapper.find("[data-request-reference]").text()).toBe(`STQ-${"A".repeat(32)}`);
    expect(wrapper.text()).toContain(onlineRequestCopy[locale].next);
    expect(wrapper.find('a[href*="wa.me"]').exists()).toBe(false);
    expect(vi.mocked(submitOnlineRequest).mock.calls[0]![0]).toMatchObject({ ...selection, locale, customer: { fullName: "Test Guest" } });
    expect(state.track).toHaveBeenCalledExactlyOnceWith("request_submitted", { item_id: selection.tourId });
    expect(JSON.stringify(state.track.mock.calls)).not.toMatch(/guest@example.com|Test Guest|Test Hotel|STQ-|201000000001|2027-01-01/);
    expect(wrapper.emitted("locked")?.at(-1)).toEqual([true]); wrapper.unmount();
  });
  it("lost response then 429 then success retains exact body/key and never says not saved", async () => {
    vi.mocked(submitOnlineRequest).mockRejectedValueOnce(new TypeError("network"))
      .mockRejectedValueOnce(new PublicApiError(429, "rate_limited"))
      .mockResolvedValueOnce({ reference: `STQ-${"B".repeat(32)}` });
    const wrapper = make(); await fill(wrapper);
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(wrapper.text()).toContain(onlineRequestCopy.en.error);
    expect(state.track).not.toHaveBeenCalled();
    expect(wrapper.find("fieldset").attributes("disabled")).toBeDefined();
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(wrapper.text()).toContain(onlineRequestCopy.en.error); expect(wrapper.text()).not.toContain(onlineRequestCopy.en.rejected);
    expect(state.track).not.toHaveBeenCalled();
    await wrapper.find("form").trigger("submit"); await flushPromises();
    const calls = vi.mocked(submitOnlineRequest).mock.calls;
    expect(calls).toHaveLength(3); expect(calls[1]![0]).toEqual(calls[0]![0]); expect(calls[2]![0]).toEqual(calls[0]![0]);
    expect(wrapper.find("[data-online-request-success]").exists()).toBe(true); wrapper.unmount();
    expect(state.track).toHaveBeenCalledExactlyOnceWith("request_submitted", { item_id: selection.tourId });
  });
  it("double submit while saving creates one request", async () => {
    let resolve!: (value: { reference: string }) => void;
    vi.mocked(submitOnlineRequest).mockImplementation(() => new Promise(done => { resolve = done; }));
    const wrapper = make(); await fill(wrapper); await wrapper.find("form").trigger("submit"); await wrapper.find("form").trigger("submit");
    expect(submitOnlineRequest).toHaveBeenCalledTimes(1); resolve({ reference: `STQ-${"C".repeat(32)}` }); await flushPromises(); wrapper.unmount();
    expect(state.track).toHaveBeenCalledExactlyOnceWith("request_submitted", { item_id: selection.tourId });
  });
  it("keeps the saved request visible if an analytics adapter throws", async () => {
    vi.mocked(submitOnlineRequest).mockResolvedValue({ reference: `STQ-${"E".repeat(32)}` });
    state.track.mockImplementationOnce(() => { throw new Error("provider unavailable"); });
    const wrapper = make(); await fill(wrapper);
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(wrapper.find("[data-online-request-success]").exists()).toBe(true);
    expect(wrapper.find("[role=alert]").exists()).toBe(false);
    expect(state.track).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });
  it("invalid selection or absent consent cannot send PII", async () => {
    const wrapper = make(false); await fill(wrapper); await wrapper.find("form").trigger("submit");
    expect(submitOnlineRequest).not.toHaveBeenCalled(); await wrapper.setProps({ valid: true }); await wrapper.find('[name="privacy"]').setValue(false); await wrapper.find("form").trigger("submit");
    expect(submitOnlineRequest).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("requires an international phone and normalizes the 00 prefix before sending", async () => {
    vi.mocked(submitOnlineRequest).mockResolvedValue({ reference: `STQ-${"D".repeat(32)}` });
    const wrapper = make(); await fill(wrapper);
    await wrapper.find('[name="phone"]').setValue("201000000001");
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(submitOnlineRequest).not.toHaveBeenCalled();
    await wrapper.find('[name="phone"]').setValue("0020 100 000 0001");
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(vi.mocked(submitOnlineRequest).mock.calls[0]![0].customer.phone).toBe("+201000000001");
    wrapper.unmount();
  });
  it("requires a plausible email for the later office confirmation", async () => {
    const wrapper = make(); await fill(wrapper);
    await wrapper.find('[name="email"]').setValue("");
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(submitOnlineRequest).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain(onlineRequestCopy.en.invalid);
    await wrapper.find('[name="email"]').setValue("invalid-email");
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(submitOnlineRequest).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
