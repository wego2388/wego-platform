import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MyBooking from "../app/pages/my-booking.vue";
import { myBookingCopy } from "../app/content/myBooking";
import { lookupReservation, PublicApiError, type PublicOnlineRequestLookup } from "../app/composables/usePublicToursApi";

const state = vi.hoisted(() => ({ locale: "en" as "en" | "ar" | "ru" | "it" }));
vi.mock("../app/composables/useSiteLocale", () => ({ useSiteLocale: () => ref(state.locale) }));
vi.mock("../app/composables/useDiscoveryCopy", () => ({ useDiscoveryCopy: () => ref({ tours: { slots: { MORNING: "Morning" } } }) }));
vi.mock("../app/composables/usePublicToursApi", async (original) => ({ ...await original<typeof import("../app/composables/usePublicToursApi")>(), lookupReservation: vi.fn() }));

const Field = defineComponent({ props: ["id", "label", "hint"], template: '<label>{{ label }}<slot :id="id" /><small>{{ hint }}</small></label>' });
const Input = defineComponent({ props: ["modelValue"], emits: ["update:modelValue"], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' });
const Button = defineComponent({ props: ["href", "loading"], template: '<a v-if="href" :href="href"><slot /></a><button v-else :disabled="loading"><slot /></button>' });
const request: PublicOnlineRequestLookup = {
  reference: `STQ-${"A".repeat(32)}`, requestStatus: "NEW", preferredDate: "2026-11-01", preferredTime: null,
  adultsCount: 1, childrenCount: 0, estimatedTotal: { amount: "35.00", currencyCode: "EUR" }, booking: null,
};
const booking = {
  reference: "STR-2026-123", tourDate: "2026-11-02", timeSlot: "MORNING" as const,
  adultsCount: 1, childrenCount: 0, totalPrice: { amount: "35.00", currencyCode: "EUR" },
  unit: null, hotelName: "Synthetic hotel", status: "CONFIRMED" as const,
};
function make() {
  return mount(MyBooking, { global: { stubs: { UiField: Field, UiInput: Input, UiButton: Button, UiBadge: { template: '<span><slot /></span>' }, ReviewRequest: true } } });
}
async function submit(wrapper: ReturnType<typeof make>, reference = request.reference, phone = "+20 100 000 0001") {
  await wrapper.find("#ref").setValue(reference);
  await wrapper.find("#lookup-phone").setValue(phone);
  await wrapper.find("form").trigger("submit");
  await flushPromises();
}
beforeEach(() => { state.locale = "en"; vi.stubGlobal("useSeoMeta", vi.fn()); });
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); });

describe("public request and booking recovery", () => {
  for (const locale of ["en", "ar", "ru", "it"] as const) {
    for (const requestStatus of ["NEW", "IN_PROGRESS", "CLOSED"] as const) {
      it(`${locale} shows ${requestStatus} as a request, not a confirmed booking`, async () => {
        state.locale = locale;
        vi.mocked(lookupReservation).mockResolvedValueOnce({ ...request, requestStatus });
        const wrapper = make();
        await submit(wrapper);
        expect(wrapper.text()).toContain(myBookingCopy[locale].request.statuses[requestStatus]);
        expect(wrapper.text()).toContain(myBookingCopy[locale].request.total);
        expect(wrapper.find("#result-heading").exists()).toBe(false);
        for (const link of wrapper.findAll("a")) expect(link.attributes("href")).not.toContain(request.reference);
        await wrapper.findAll("button").find(button => button.text() === myBookingCopy[locale].another)!.trigger("click");
        expect(wrapper.find("form").exists()).toBe(true);
        expect(wrapper.find("[data-request-lookup-result]").exists()).toBe(false);
        wrapper.unmount();
      });
    }
  }
  it("keeps the STQ reference usable after conversion and shows current cancellation", async () => {
    vi.mocked(lookupReservation).mockResolvedValueOnce({ ...request, requestStatus: "CONVERTED", booking: { ...booking, status: "CANCELLED" } });
    const wrapper = make(); await submit(wrapper);
    expect(wrapper.text()).toContain(request.reference);
    expect(wrapper.text()).toContain(booking.reference);
    expect(wrapper.text()).toContain(myBookingCopy.en.statuses.CANCELLED);
    wrapper.unmount();
  });
  it("retains STR results and normalizes Arabic phone and reference digits", async () => {
    vi.mocked(lookupReservation).mockResolvedValueOnce(booking);
    const wrapper = make(); await submit(wrapper, "str-٢٠٢٦-١٢٣", "٠٠٢٠١٠٠٠٠٠٠٠٠١");
    expect(lookupReservation).toHaveBeenCalledWith("STR-2026-123", "+201000000001");
    expect(wrapper.find("#result-heading").exists()).toBe(true);
    expect(wrapper.find("[data-request-lookup-result]").exists()).toBe(false);
    wrapper.unmount();
  });
  for (const status of [400, 404, 429, 503]) {
    it(`shows a truthful retryable error for HTTP ${status}`, async () => {
      vi.mocked(lookupReservation).mockRejectedValueOnce(new PublicApiError(status, "test"));
      const wrapper = make(); await submit(wrapper);
      expect(wrapper.find('[role="alert"]').text()).toBe(status === 429 ? myBookingCopy.en.tooMany : status === 503 ? myBookingCopy.en.network : myBookingCopy.en.notFound);
      expect(wrapper.find("form").exists()).toBe(true); wrapper.unmount();
    });
  }
  it("never sends missing knowledge factors", async () => {
    const wrapper = make(); await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(lookupReservation).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain(myBookingCopy.en.required); wrapper.unmount();
  });
});
