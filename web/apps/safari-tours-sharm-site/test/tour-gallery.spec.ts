import { mount } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import TourGallery from "../app/components/tour/TourGallery.vue";
import { tourPageCopy } from "../app/content/tourPage";
const state = vi.hoisted(() => ({ locale: "en" as "en" | "ar", key: undefined as ((event: KeyboardEvent) => void) | undefined }));
vi.mock("../app/composables/useSiteLocale", () => ({ useSiteLocale: () => ref(state.locale) }));
const media = [1, 2, 3].map(index => ({ path: `/media/tours/fixture/photo-${index}.jpg`, width: 1200, height: 800, alt: `Actual fixture image ${index}`, isCover: index === 1 }));
function make() {
  vi.stubGlobal("useEventListener", (_event: string, callback: (event: KeyboardEvent) => void) => { state.key = callback; });
  return mount(TourGallery, { props: { media }, global: { stubs: {
    BrandSafeTourImage: { props: ["src", "alt"], template: '<img :src="src" :alt="alt">' },
    UiButton: { template: '<button><slot /></button>' },
    UiDialog: { props: ["open"], template: '<section v-if="open" role="dialog"><slot /></section>' },
  } } });
}
afterEach(() => { state.locale = "en"; state.key = undefined; document.documentElement.dir = "ltr"; vi.unstubAllGlobals(); });
describe("approved multi-photo tour gallery", () => {
  it("opens the selected photo, wraps keyboard navigation and closes safely when media is removed", async () => {
    const wrapper = make(); await wrapper.findAll("li button")[1]!.trigger("click");
    expect(wrapper.find('[role="dialog"] img').attributes("src")).toBe(media[1]!.path);
    const event = new KeyboardEvent("keydown", { key: "ArrowRight", cancelable: true }); state.key!(event);
    await wrapper.vm.$nextTick(); expect(event.defaultPrevented).toBe(true);
    expect(wrapper.find('[role="dialog"] img').attributes("src")).toBe(media[2]!.path);
    await wrapper.setProps({ media: [] }); expect(wrapper.find('[role="dialog"]').exists()).toBe(false); wrapper.unmount();
  });
  it("uses Arabic direction and supports actual touch swiping, without hijacking vertical scrolling", async () => {
    state.locale = "ar"; document.documentElement.dir = "rtl";
    const wrapper = make(); await wrapper.findAll("li button")[0]!.trigger("click");
    const surface = wrapper.find(".touch-pan-y");
    await surface.trigger("touchstart", { changedTouches: [{ clientX: 200, clientY: 20 }] });
    await surface.trigger("touchend", { changedTouches: [{ clientX: 100, clientY: 25 }] });
    expect(wrapper.find('[role="dialog"] img').attributes("src")).toBe(media[2]!.path);
    await surface.trigger("touchstart", { changedTouches: [{ clientX: 200, clientY: 20 }] });
    await surface.trigger("touchend", { changedTouches: [{ clientX: 180, clientY: 200 }] });
    expect(wrapper.find('[role="dialog"] img').attributes("src")).toBe(media[2]!.path);
    expect(wrapper.text()).toContain(tourPageCopy.ar.gallery.counter(3, 3)); wrapper.unmount();
  });
  it("disables previous/next for one photo and shows its factual caption", async () => {
    const wrapper = make(); await wrapper.setProps({ media: [media[0]!] });
    await wrapper.find("li button").trigger("click");
    expect(wrapper.findAll('[role="dialog"] button').every(button => button.attributes("disabled") !== undefined)).toBe(true);
    expect(wrapper.find('[role="dialog"]').text()).toContain(media[0]!.alt); wrapper.unmount();
  });
});
