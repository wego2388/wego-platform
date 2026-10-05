import { mount, flushPromises } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import StaffPhotoPreview from "../app/components/StaffPhotoPreview.vue";
import { fetchPrivatePhoto } from "../app/composables/useTourMediaApi";

vi.mock("../app/composables/useTourMediaApi", async importOriginal => ({ ...(await importOriginal<object>()), fetchPrivatePhoto: vi.fn() }));
const id = "16f34404-0bc9-4cf0-ae8b-3c60f7d5f7d0";
const path = `/media/tours/real-tour/${id}.jpg`;
const props = { path, token: "private-token", alt: "Actual photo", width: 1200, height: 800, failureLabel: "Preview failed" };
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); });
describe("staff photo preview never exposes bearer credentials", () => {
  it("uses authenticated blob URL and revokes it on unmount", async () => {
    const createObjectURL = vi.fn(() => "blob:staff-preview"); const revokeObjectURL = vi.fn();
    vi.stubGlobal("URL", { createObjectURL, revokeObjectURL });
    vi.mocked(fetchPrivatePhoto).mockResolvedValueOnce(new Blob(["image"], { type: "image/jpeg" }));
    const wrapper = mount(StaffPhotoPreview, { props }); await flushPromises();
    expect(fetchPrivatePhoto).toHaveBeenCalledExactlyOnceWith("private-token", id, "w768");
    expect(wrapper.find("img").attributes("src")).toBe("blob:staff-preview"); expect(wrapper.html()).not.toContain("private-token");
    await wrapper.find("img").trigger("load"); expect(wrapper.emitted("ready")?.at(-1)).toEqual([true]);
    wrapper.unmount(); expect(revokeObjectURL).toHaveBeenCalledWith("blob:staff-preview");
  });
  it("ignores a late response and does not create a blob URL after unmount", async () => {
    let resolve!: (blob: Blob) => void; const promise = new Promise<Blob>(yes => { resolve = yes; });
    const createObjectURL = vi.fn(); vi.stubGlobal("URL", { createObjectURL, revokeObjectURL: vi.fn() });
    vi.mocked(fetchPrivatePhoto).mockReturnValueOnce(promise);
    const wrapper = mount(StaffPhotoPreview, { props }); wrapper.unmount(); resolve(new Blob(["image"], { type: "image/jpeg" })); await flushPromises();
    expect(createObjectURL).not.toHaveBeenCalled();
  });
  it("shows a factual failed preview and never emits ready on rejected bytes", async () => {
    vi.mocked(fetchPrivatePhoto).mockRejectedValueOnce(new Error("private diagnostic"));
    const wrapper = mount(StaffPhotoPreview, { props }); await flushPromises();
    expect(wrapper.text()).toBe("Preview failed"); expect(wrapper.find("img").exists()).toBe(false); expect(wrapper.emitted("ready")).toEqual([[false]]);
    wrapper.unmount();
  });
  it("does not request arbitrary external/path/query input as a legacy image", async () => {
    const wrapper = mount(StaffPhotoPreview, { props: { ...props, path: "https://remote.example/pixel?token=bad" } }); await flushPromises();
    expect(fetchPrivatePhoto).not.toHaveBeenCalled(); expect(wrapper.find("img").exists()).toBe(false); expect(wrapper.text()).toBe("Preview failed"); wrapper.unmount();
  });
  it("uses base for small original images instead of requesting an absent larger derivative", async () => {
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:small"), revokeObjectURL: vi.fn() });
    vi.mocked(fetchPrivatePhoto).mockResolvedValueOnce(new Blob(["image"], { type: "image/jpeg" }));
    const wrapper = mount(StaffPhotoPreview, { props: { ...props, width: 600, height: 400 } }); await flushPromises();
    expect(fetchPrivatePhoto).toHaveBeenCalledExactlyOnceWith("private-token", id, "base"); expect(wrapper.find("img").attributes("src")).toBe("blob:small"); wrapper.unmount();
  });
  it("previews a category cover using its server asset ID and bearer blob, never its draft public path", async () => {
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:category"), revokeObjectURL: vi.fn() });
    vi.mocked(fetchPrivatePhoto).mockResolvedValueOnce(new Blob(["image"], { type: "image/jpeg" }));
    const categoryPath = `/media/categories/SEA/${id}.jpg`;
    const wrapper = mount(StaffPhotoPreview, { props: { ...props, path: categoryPath, assetId: id, width: 600, height: 400 } }); await flushPromises();
    expect(fetchPrivatePhoto).toHaveBeenCalledExactlyOnceWith("private-token", id, "base"); expect(wrapper.find("img").attributes("src")).toBe("blob:category"); expect(wrapper.html()).not.toContain(categoryPath); wrapper.unmount();
  });
});
