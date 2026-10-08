import { mount, flushPromises, type VueWrapper } from "@vue/test-utils";
import { defineComponent, onMounted } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { StaffTourContent } from "@wego/api-contract";
import TourMediaEditor from "../app/components/TourMediaEditor.vue";
import { approveTourPhoto, replaceTourMedia, uploadTourPhoto } from "../app/composables/useTourMediaApi";
import { ToursApiError } from "../app/composables/useToursApi";

vi.mock("../app/composables/useTourMediaApi", async importOriginal => ({ ...(await importOriginal<object>()), approveTourPhoto: vi.fn(), replaceTourMedia: vi.fn(), uploadTourPhoto: vi.fn() }));
type Photo = StaffTourContent["media"][number];
const photo: Photo = { id: "media-id", path: "/media/tours/real-tour/16f34404-0bc9-4cf0-ae8b-3c60f7d5f7d0.jpg", width: 1200, height: 800, position: 0, isCover: true, alt: { en: "Real beach", ar: "شاطئ حقيقي" }, rightsStatus: "DRAFT", revision: "reviewed-v1" };
const Preview = defineComponent({ props: ["path", "token", "alt", "width", "height", "failureLabel"], emits: ["ready"], setup(_props, { emit }) { onMounted(() => emit("ready", true)); }, template: '<div class="preview">Actual image preview</div>' });
function make(overrides: Record<string, unknown> = {}) {
  return mount(TourMediaEditor, { props: { tourId: "tour-id", token: "secret", media: [{ ...photo, alt: { ...photo.alt } }], mediaRevision: "whole-list-v1", staffLocale: "en", canManage: true, canUpload: true, canPublish: true, locked: false, verified: true, refresh: vi.fn(async () => true), ...overrides }, global: { stubs: { StaffPhotoPreview: Preview } } });
}
const button = (wrapper: VueWrapper, text: string) => { const found = wrapper.findAll("button").find(entry => entry.text() === text); if (!found) throw new Error(`Missing button: ${text}`); return found; };
afterEach(() => { vi.clearAllMocks(); vi.useRealTimers(); });
async function approvedSelection(wrapper: VueWrapper) {
  const input = wrapper.find("#photo-file");
  Object.defineProperty(input.element, "files", { configurable: true, value: [new File(["jpeg"], "photo.jpg", { type: "image/jpeg" })] });
  await input.trigger("change");
  await wrapper.find("#upload-alt-en").setValue("A quad bike on sand");
  await wrapper.find('form img').trigger("load");
  await wrapper.find("#upload-photo-rights").setValue(true);
}
const uploadedPhoto: Photo = { ...photo, id: "new-photo", alt: { en: "A quad bike on sand" }, revision: "uploaded-v1" };
const uploadedAsset = "16f34404-0bc9-4cf0-ae8b-3c60f7d5f7d0";
describe("tour photos owner workflow", () => {
  it("uploads and approves the exact server asset/revision in one confirmed owner action", async () => {
    vi.mocked(uploadTourPhoto).mockResolvedValueOnce({ assetId: uploadedAsset, mediaId: "new-photo" });
    vi.mocked(approveTourPhoto).mockResolvedValueOnce(undefined);
    let reads = 0;
    const refresh = vi.fn(async () => { await wrapper.setProps({ media: [{ ...uploadedPhoto, rightsStatus: ++reads === 1 ? "DRAFT" : "APPROVED" }], mediaRevision: `list-${reads}` }); return true; });
    const wrapper = make({ media: [], refresh }); await approvedSelection(wrapper);
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(approveTourPhoto).toHaveBeenCalledExactlyOnceWith("secret", "tour-id", "new-photo", "uploaded-v1");
    expect(uploadTourPhoto).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("Photo uploaded, rights approved and verified");
    expect(wrapper.find('form img').exists()).toBe(false); wrapper.unmount();
  });
  for (const changed of ["asset", "alt", "additionalAlt", "identity"] as const) {
    it(`never approves a ${changed} that changed after the upload snapshot`, async () => {
      vi.mocked(uploadTourPhoto).mockResolvedValueOnce({ assetId: uploadedAsset, mediaId: "new-photo" });
      const refresh = vi.fn(async () => {
        await wrapper.setProps({ media: [{ ...uploadedPhoto, ...(changed === "asset" ? { path: "/media/tours/real-tour/00000000-0000-0000-0000-000000000000.jpg" } : changed === "alt" ? { alt: { en: "Unreviewed other description" } } : changed === "additionalAlt" ? { alt: { ...uploadedPhoto.alt, ru: "Unreviewed concurrent description" } } : {}) }], ...(changed === "identity" ? { token: "another-session" } : {}) }); return true;
      });
      const wrapper = make({ media: [], refresh }); await approvedSelection(wrapper);
      await wrapper.find("form").trigger("submit"); await flushPromises();
      expect(approveTourPhoto).not.toHaveBeenCalled(); expect(uploadTourPhoto).toHaveBeenCalledTimes(1);
      expect(wrapper.text()).toContain("approval could not be verified"); wrapper.unmount();
    });
  }
  it("normalizes omitted blank optional descriptions exactly as the upload API does", async () => {
    vi.mocked(uploadTourPhoto).mockResolvedValueOnce({ assetId: uploadedAsset, mediaId: "new-photo" });
    vi.mocked(approveTourPhoto).mockResolvedValueOnce(undefined);
    let reads = 0;
    const refresh = vi.fn(async () => { await wrapper.setProps({ media: [{ ...uploadedPhoto, rightsStatus: ++reads === 1 ? "DRAFT" : "APPROVED" }] }); return true; });
    const wrapper = make({ media: [], refresh }); await approvedSelection(wrapper);
    await wrapper.find("#upload-alt-ru").setValue("   "); await wrapper.find("#upload-photo-rights").setValue(true);
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(approveTourPhoto).toHaveBeenCalledExactlyOnceWith("secret", "tour-id", "new-photo", "uploaded-v1"); wrapper.unmount();
  });
  it("does not claim approval or resend the file after a failed approval response", async () => {
    vi.mocked(uploadTourPhoto).mockResolvedValueOnce({ assetId: uploadedAsset, mediaId: "new-photo" });
    vi.mocked(approveTourPhoto).mockRejectedValueOnce(new ToursApiError(409, "draft_changed"));
    const refresh = vi.fn(async () => { await wrapper.setProps({ media: [uploadedPhoto] }); return true; });
    const wrapper = make({ media: [], refresh }); await approvedSelection(wrapper);
    await wrapper.find("form").trigger("submit"); await flushPromises();
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(uploadTourPhoto).toHaveBeenCalledTimes(1); expect(approveTourPhoto).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("do not upload it again"); expect(wrapper.text()).not.toContain("Photo uploaded, rights approved and verified"); wrapper.unmount();
  });
  it("requires publish permission, a decoded local preview and English description; editing resets confirmation", async () => {
    const denied = make({ canPublish: false }); expect(denied.find("#upload-photo-rights").exists()).toBe(false); denied.unmount();
    const wrapper = make(); await approvedSelection(wrapper);
    expect((wrapper.find("#upload-photo-rights").element as HTMLInputElement).checked).toBe(true);
    await wrapper.find("#upload-alt-en").setValue("A different description");
    expect((wrapper.find("#upload-photo-rights").element as HTMLInputElement).checked).toBe(false);
    await wrapper.find('form img').trigger("error");
    expect(wrapper.find("#upload-photo-rights").attributes("disabled")).toBeDefined(); wrapper.unmount();
  });
  it("uses readonly image paths/dimensions and only edits localized alt/cover/order", async () => {
    const wrapper = make(); await flushPromises();
    await wrapper.find("#photo-alt-media-id-en").setValue("Verified new alt");
    expect(wrapper.emitted("dirty")?.at(-1)).toEqual([true]);
    expect(button(wrapper, "Review photo rights").attributes("disabled")).toBeDefined();
    await button(wrapper, "Save photo metadata").trigger("click"); await flushPromises();
    expect(replaceTourMedia).toHaveBeenCalledExactlyOnceWith("secret", "tour-id", [{ id: "media-id", path: photo.path, width: 1200, height: 800, isCover: true, alt: { en: "Verified new alt", ar: "شاطئ حقيقي" } }], "whole-list-v1");
    expect(approveTourPhoto).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("requires visible reviewed image and explicit rights confirmation before approval", async () => {
    const wrapper = make(); await flushPromises();
    await button(wrapper, "Review photo rights").trigger("click"); await flushPromises();
    expect(wrapper.find('[role="dialog"]').text()).toContain("reviewed-v1");
    expect(button(wrapper, "Confirm rights and approve").attributes("disabled")).toBeDefined();
    await wrapper.find('[role="dialog"] input[type="checkbox"]').setValue(true);
    await button(wrapper, "Confirm rights and approve").trigger("click"); await flushPromises();
    expect(approveTourPhoto).toHaveBeenCalledExactlyOnceWith("secret", "tour-id", "media-id", "reviewed-v1"); wrapper.unmount();
  });
  it("offers no uploader or edits without server-required permissions", async () => {
    const wrapper = make({ canUpload: false, canManage: false, canPublish: false }); await flushPromises();
    expect(wrapper.find("#photo-file").exists()).toBe(false); expect(wrapper.find("fieldset").attributes("disabled")).toBeDefined();
    expect(wrapper.findAll("button").some(entry => entry.text() === "Review photo rights")).toBe(false); wrapper.unmount();
  });
  it("preserves unsaved photo metadata across unrelated parent refreshes", async () => {
    const wrapper = make(); await wrapper.find("#photo-alt-media-id-en").setValue("Local unsaved alt");
    await wrapper.setProps({ media: [{ ...photo, alt: { en: "Concurrent server alt" }, revision: "remote-v2" }], mediaRevision: "whole-list-v2" });
    expect((wrapper.find("#photo-alt-media-id-en").element as HTMLInputElement).value).toBe("Local unsaved alt");
    expect(wrapper.emitted("dirty")?.at(-1)).toEqual([true]);
    expect(button(wrapper, "Save photo metadata").attributes("disabled")).toBeDefined();
    expect(wrapper.text()).toContain("saved photo list changed");
    await button(wrapper, "Discard local photo edits & reload list").trigger("click");
    expect((wrapper.find("#photo-alt-media-id-en").element as HTMLInputElement).value).toBe("Concurrent server alt"); wrapper.unmount();
  });
  it("keeps edits after server OCC rejection, refreshes once, and never retries stale list writes", async () => {
    vi.mocked(replaceTourMedia).mockRejectedValueOnce(new ToursApiError(409, "draft_changed"));
    const refresh = vi.fn(async () => { await wrapper.setProps({ media: [{ ...photo, alt: { en: "Newest uploaded photo" }, revision: "image-v2" }], mediaRevision: "whole-list-v2" }); return true; });
    const wrapper = make({ refresh }); await flushPromises();
    await wrapper.find("#photo-alt-media-id-en").setValue("Keep local alt edit");
    await button(wrapper, "Save photo metadata").trigger("click"); await flushPromises();
    expect(replaceTourMedia).toHaveBeenCalledTimes(1); expect(refresh).toHaveBeenCalledTimes(1);
    expect((wrapper.find("#photo-alt-media-id-en").element as HTMLInputElement).value).toBe("Keep local alt edit");
    expect(button(wrapper, "Save photo metadata").attributes("disabled")).toBeDefined();
    expect(wrapper.text()).toContain("saved photo list changed"); expect(wrapper.text()).not.toContain("Photo metadata saved and verified."); wrapper.unmount();
  });
  it("rejects oversized/type-spoofed client selection and never uploads invalid input", async () => {
    const wrapper = make();
    const input = wrapper.find("#photo-file"); Object.defineProperty(input.element, "files", { configurable: true, value: [new File(["<svg>"], "photo.jpg", { type: "image/svg+xml" })] });
    await input.trigger("change"); await flushPromises();
    expect(wrapper.text()).toContain("Choose a non-empty JPEG/PNG");
    expect(button(wrapper, "Upload photo").attributes("disabled")).toBeDefined(); expect(uploadTourPhoto).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("one-shot failed upload rereads server state and disables blind repeated submissions", async () => {
    vi.mocked(uploadTourPhoto).mockRejectedValueOnce(new TypeError("connection lost"));
    const refresh = vi.fn(async () => true); const wrapper = make({ refresh });
    const input = wrapper.find("#photo-file"); Object.defineProperty(input.element, "files", { configurable: true, value: [new File(["jpeg"], "photo.jpg", { type: "image/jpeg" })] });
    await input.trigger("change"); await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(uploadTourPhoto).toHaveBeenCalledTimes(1); expect(refresh).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("Upload result could not be verified"); expect(button(wrapper, "Upload photo").attributes("disabled")).toBeDefined();
    expect(vi.mocked(uploadTourPhoto).mock.calls[0]?.[4]).toMatch(/^[0-9a-f-]{36}$/); wrapper.unmount();
  });
  it("replacement includes the current media ID and reviewed revision, without changing the path manually", async () => {
    vi.mocked(uploadTourPhoto).mockResolvedValueOnce({ assetId: "new-asset", mediaId: "media-id" });
    const wrapper = make(); await wrapper.find("#photo-replacement").setValue("media-id");
    const input = wrapper.find("#photo-file"); const file = new File(["jpeg"], "photo.jpg", { type: "image/jpeg" }); Object.defineProperty(input.element, "files", { configurable: true, value: [file] });
    await input.trigger("change"); await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(uploadTourPhoto).toHaveBeenCalledWith("secret", "tour-id", file, { en: "Real beach", ar: "شاطئ حقيقي" }, expect.any(String), { mediaId: "media-id", revision: "reviewed-v1" }); wrapper.unmount();
  });
  it("renders complete Arabic owner controls without turning unknown reviews into claims", async () => {
    const wrapper = make({ staffLocale: "ar" }); await flushPromises(); expect(wrapper.text()).toContain("صور الرحلة"); expect(wrapper.text()).toContain("مراجعة حقوق الصورة"); expect(wrapper.text()).toContain("الحقوق غير معتمدة"); wrapper.unmount();
  });
});
