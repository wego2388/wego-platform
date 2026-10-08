import { mount, flushPromises, type VueWrapper } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { StaffTourContent } from "@wego/api-contract";
import ContentPage from "../app/pages/tours/[id]/content.vue";
import { getStaffTourContent, publishTourContent, saveTourContentDraft, saveTourFactsDraft } from "../app/composables/useTourContentApi";
import { getStaffTour } from "../app/composables/useToursApi";
import { writeAuthSession } from "../app/composables/useAuthSession";

vi.mock("../app/composables/useTourContentApi", async importOriginal => ({ ...(await importOriginal<object>()), getStaffTourContent: vi.fn(), saveTourContentDraft: vi.fn(), publishTourContent: vi.fn(), unpublishTourContent: vi.fn(), saveTourFactsDraft: vi.fn(), publishTourFacts: vi.fn(), unpublishTourFacts: vi.fn() }));
vi.mock("../app/composables/useToursApi", async importOriginal => ({ ...(await importOriginal<object>()), getStaffTour: vi.fn() }));
const permissions = ["tours-operator.tour:view", "tours-operator.tour:manage", "tours-operator.content:publish", "tours-operator.media:upload"];
const registerLeave = vi.fn<(guard: () => boolean) => void>();
const initial = (): StaffTourContent => ({ content: { en: [{ stage: "DRAFT", revision: "reviewed-en-v1", updatedAt: "2026-10-05T10:00:00Z", document: { name: "Owner title", shortDescription: "Owner summary", description: "Owner description", includes: ["First included item"], stops: [] } }] }, facts: [], media: [], mediaRevision: "whole-list-v1" });
const button = (wrapper: VueWrapper, text: string) => { const found = wrapper.findAll("button").find(entry => entry.text().trim().startsWith(text)); if (!found) throw new Error(`Missing button: ${text}`); return found; };
beforeEach(() => {
  sessionStorage.clear(); writeAuthSession({ token: "private-token", email: "fixture@example.invalid", roles: [], permissions });
  vi.stubGlobal("definePageMeta", vi.fn()); vi.stubGlobal("useRoute", () => ({ params: { id: "tour-id" } })); vi.stubGlobal("useRouter", () => ({ replace: vi.fn() })); vi.stubGlobal("useHead", vi.fn());
  vi.stubGlobal("onBeforeRouteLeave", registerLeave);
  vi.stubGlobal("useCookie", () => ref("en")); vi.stubGlobal("useState", (_key: string, factory: () => unknown) => ref(factory()));
  vi.mocked(getStaffTourContent).mockResolvedValue(initial()); vi.mocked(getStaffTour).mockRejectedValue(new Error("optional metadata unavailable"));
});
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear(); });
const make = () => mount(ContentPage, { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" }, TourMediaEditor: { template: '<div class="photo-editor" />' } } } });

describe("EN/AR catalog editor page", () => {
  it.each([["0", 0], ["12", 12], ["99", 99], ["", null]])("native minimum-age input %s saves the fact without a coercion exception", async (value, expected) => {
    let state = initial();
    vi.mocked(getStaffTourContent).mockImplementation(async () => state);
    vi.mocked(saveTourFactsDraft).mockImplementation(async (_token, _id, document) => {
      state = { ...state, facts: [{ stage: "DRAFT", revision: "saved-age-v2", updatedAt: "2026-10-08T18:00:00Z", document }] };
    });
    const wrapper = make(); await flushPromises();
    await wrapper.find("#facts-age").setValue("18");
    await wrapper.find("#facts-age").setValue(value);
    if (expected === null) await wrapper.find("#facts-children").setValue("true");
    await wrapper.findAll("form")[1]!.trigger("submit"); await flushPromises();
    expect(saveTourFactsDraft).toHaveBeenCalledExactlyOnceWith("private-token", "tour-id", expect.objectContaining({ minimumAge: expected }));
    expect((wrapper.find("#facts-age").element as HTMLInputElement).value).toBe(value);
    expect(wrapper.text()).toContain("Draft saved and refreshed from the server.");
    wrapper.unmount();
  });
  it("preserves separate English/Arabic unsaved drafts and real multiline editing", async () => {
    const wrapper = make(); await flushPromises();
    await wrapper.find("#content-name").setValue("Local English title");
    await button(wrapper, "العربية").trigger("click");
    expect((wrapper.find("#content-name").element as HTMLInputElement).value).toBe("");
    expect(wrapper.find("#content-name").attributes("dir")).toBe("rtl");
    await wrapper.find("#content-name").setValue("عنوان محلي");
    await button(wrapper, "English").trigger("click");
    expect((wrapper.find("#content-name").element as HTMLInputElement).value).toBe("Local English title");
    const included = wrapper.find("#content-includes");
    await included.setValue("First\n"); expect((included.element as HTMLTextAreaElement).value).toBe("First\n");
    await included.setValue("First\nSecond\n"); await included.trigger("blur");
    expect((included.element as HTMLTextAreaElement).value).toBe("First\nSecond");
    wrapper.unmount();
  });
  it("shows the exact saved snapshot for review and sends its revision rather than unsaved copy", async () => {
    const wrapper = make(); await flushPromises();
    await button(wrapper, "Review & publish").trigger("click"); await flushPromises();
    const review = wrapper.find('[role="dialog"]'); expect(review.text()).toContain("Owner title"); expect(review.text()).toContain("reviewed-en-v1");
    expect(wrapper.find("fieldset").attributes("disabled")).toBeDefined();
    await button(wrapper, "Publish this revision").trigger("click"); await flushPromises();
    expect(publishTourContent).toHaveBeenCalledExactlyOnceWith("private-token", "tour-id", "en", "reviewed-en-v1");
    expect(saveTourContentDraft).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("never enables publish for unsaved content and protects navigation", async () => {
    const wrapper = make(); await flushPromises();
    await wrapper.find("#content-name").setValue("Unsaved owner change");
    expect(button(wrapper, "Review & publish").attributes("disabled")).toBeDefined();
    const confirm = vi.fn(() => false); vi.stubGlobal("confirm", confirm);
    const guard = registerLeave.mock.calls[0]?.[0];
    expect(guard?.()).toBe(false); expect(confirm).toHaveBeenCalled(); wrapper.unmount();
  });
  it("saves only the selected locale and displays verified success after reread", async () => {
    let state = initial(); vi.mocked(getStaffTourContent).mockImplementation(async () => state);
    vi.mocked(saveTourContentDraft).mockImplementation(async (_token, _id, language, document) => { state = { ...state, content: { ...state.content, [language]: [{ stage: "DRAFT", revision: "saved-v2", updatedAt: "2026-10-05T11:00:00Z", document }] } }; });
    const wrapper = make(); await flushPromises();
    await wrapper.find("#content-name").setValue("Approved new owner title");
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(saveTourContentDraft).toHaveBeenCalledWith("private-token", "tour-id", "en", expect.objectContaining({ name: "Approved new owner title" }));
    expect(wrapper.text()).toContain("Draft saved and refreshed from the server."); expect(publishTourContent).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("keeps unknown facts visibly distinct and handles multiple guide codes without losing the comma", async () => {
    const wrapper = make(); await flushPromises();
    expect((wrapper.find("#facts-children").element as HTMLSelectElement).value).toBe("");
    expect((wrapper.find("#facts-age").element as HTMLInputElement).value).toBe("");
    await wrapper.find("#facts-languages").setValue("en,");
    expect((wrapper.find("#facts-languages").element as HTMLInputElement).value).toBe("en, ");
    await wrapper.find("#facts-languages").setValue("en, ar");
    expect((wrapper.find("#facts-languages").element as HTMLInputElement).value).toBe("en, ar"); wrapper.unmount();
  });
  it("renders Arabic staff UI while keeping the independently selected English document LTR", async () => {
    vi.stubGlobal("useCookie", () => ref("ar")); const wrapper = make(); await flushPromises();
    expect(wrapper.text()).toContain("المحتوى والصور"); expect(wrapper.text()).toContain("حقائق الرحلة المشتركة");
    expect(wrapper.find("#content-name").attributes("lang")).toBe("en"); expect(wrapper.find("#content-name").attributes("dir")).toBe("ltr"); wrapper.unmount();
  });
  it("denies editor access without the backend content-view permission", async () => {
    writeAuthSession({ token: "token", email: "fixture@example.invalid", roles: [], permissions: ["tours-operator.tour:manage"] });
    const wrapper = make(); await flushPromises(); expect(wrapper.find("#content-name").exists()).toBe(false); expect(getStaffTourContent).not.toHaveBeenCalled(); wrapper.unmount();
  });
});
