import { mount, flushPromises, type VueWrapper } from "@vue/test-utils";
import { defineComponent, onMounted, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CategoryMedia } from "@wego/api-contract";
import CategoryPage from "../app/pages/categories.vue";
import { approveCategoryCover, getCategoryMedia, listCategoryMedia, saveCategoryAlt, uploadCategoryCover } from "../app/composables/useCategoryMediaApi";
import { COVER_CATEGORIES } from "../app/composables/useCategoryCoverEditor";
import { writeAuthSession } from "../app/composables/useAuthSession";
import { ToursApiError } from "../app/composables/useToursApi";

vi.mock("../app/composables/useCategoryMediaApi", async importOriginal => ({ ...(await importOriginal<object>()), listCategoryMedia: vi.fn(), getCategoryMedia: vi.fn(), saveCategoryAlt: vi.fn(), uploadCategoryCover: vi.fn(), approveCategoryCover: vi.fn() }));
const permissions = ["tours-operator.tour:view", "tours-operator.tour:manage", "tours-operator.content:publish", "tours-operator.media:upload"];
const assetId = "16f34404-0bc9-4cf0-ae8b-3c60f7d5f7d0";
const initial = (): CategoryMedia[] => COVER_CATEGORIES.map(category => ({ category, revision: `reviewed-${category}-v1`, alt: { en: `Verified ${category} photo` }, assetId, path: `/media/categories/${category}/${assetId}.jpg`, width: 600, height: 400, rightsStatus: "DRAFT", approvedAt: null, updatedAt: "2026-10-05T10:00:00Z" }));
let state: CategoryMedia[];
const registerLeave = vi.fn<(guard: () => boolean) => void>();
const Preview = defineComponent({ props: ["path", "assetId", "token", "alt", "width", "height", "failureLabel"], emits: ["ready"], setup(_props, { emit }) { onMounted(() => emit("ready", true)); }, template: '<div class="private-photo-preview">Actual authenticated preview</div>' });
const button = (wrapper: VueWrapper, text: string) => { const found = wrapper.findAll("button").find(entry => entry.text().trim().startsWith(text)); if (!found) throw new Error(`Missing button: ${text}`); return found; };
beforeEach(() => {
  state = initial(); sessionStorage.clear(); writeAuthSession({ token: "secret", email: "fixture@example.invalid", roles: [], permissions });
  vi.stubGlobal("useRouter", () => ({ replace: vi.fn() })); vi.stubGlobal("useHead", vi.fn()); vi.stubGlobal("useCookie", () => ref("en")); vi.stubGlobal("useState", (_key: string, factory: () => unknown) => ref(factory())); vi.stubGlobal("onBeforeRouteLeave", registerLeave);
  vi.mocked(listCategoryMedia).mockImplementation(async () => state); vi.mocked(getCategoryMedia).mockImplementation(async (_token, category) => state.find(row => row.category === category)!);
});
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear(); });
const make = () => mount(CategoryPage, { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" }, StaffPhotoPreview: Preview } } });

describe("existing category cover owner workflow", () => {
  it("shows exactly five known categories and preserves multilingual text when switching and refreshing", async () => {
    const wrapper = make(); await flushPromises();
    expect(wrapper.findAll("button[aria-pressed]")).toHaveLength(5);
    await wrapper.find("#category-alt-en").setValue("Local desert text"); await wrapper.find("#category-alt-ar").setValue("وصف محلي للصحراء");
    await button(wrapper, "Sea").trigger("click"); expect((wrapper.find("#category-alt-en").element as HTMLInputElement).value).toBe("Verified SEA photo");
    await wrapper.find("#category-alt-ru").setValue("Местное описание"); await button(wrapper, "Desert").trigger("click");
    expect((wrapper.find("#category-alt-en").element as HTMLInputElement).value).toBe("Local desert text");
    expect(wrapper.find("#category-alt-ar").attributes("dir")).toBe("rtl");
    await button(wrapper, "Refresh categories").trigger("click"); await flushPromises(); expect((wrapper.find("#category-alt-ar").element as HTMLInputElement).value).toBe("وصف محلي للصحراء"); wrapper.unmount();
  });
  it("saves current category revision only and does not approve it automatically", async () => {
    vi.mocked(saveCategoryAlt).mockImplementation(async (_token, category, _revision, alt) => { state = state.map(row => row.category === category ? { ...row, alt, revision: "saved-v2", rightsStatus: "DRAFT", approvedAt: null } : row); });
    const wrapper = make(); await flushPromises(); await wrapper.find("#category-alt-en").setValue("Verified new desert alt");
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect(saveCategoryAlt).toHaveBeenCalledExactlyOnceWith("secret", "DESERT", "reviewed-DESERT-v1", { en: "Verified new desert alt" });
    expect(wrapper.text()).toContain("Category alternative text saved and verified."); expect(approveCategoryCover).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("blocks stale revision overwrite and retains local text until explicit reset", async () => {
    vi.mocked(saveCategoryAlt).mockImplementation(async () => { state = state.map(row => row.category === "DESERT" ? { ...row, revision: "remote-v2", alt: { en: "Other editor new text" } } : row); throw new ToursApiError(409, "draft_changed"); });
    const wrapper = make(); await flushPromises(); await wrapper.find("#category-alt-en").setValue("Keep local text");
    await wrapper.find("form").trigger("submit"); await flushPromises();
    expect((wrapper.find("#category-alt-en").element as HTMLInputElement).value).toBe("Keep local text"); expect(wrapper.text()).toContain("writes are blocked to protect newer data");
    expect(wrapper.find("fieldset").attributes("disabled")).toBeDefined(); expect(saveCategoryAlt).toHaveBeenCalledTimes(1);
    await button(wrapper, "Discard local text & reload saved copy").trigger("click"); expect((wrapper.find("#category-alt-en").element as HTMLInputElement).value).toBe("Other editor new text"); wrapper.unmount();
  });
  it("requires actual image readiness and rights confirmation, then sends exact reviewed revision", async () => {
    vi.mocked(approveCategoryCover).mockImplementation(async (_token, category) => { state = state.map(row => row.category === category ? { ...row, rightsStatus: "APPROVED", approvedAt: "2026-10-05T11:00:00Z" } : row); });
    const wrapper = make(); await flushPromises(); await button(wrapper, "Review category photo rights").trigger("click"); await flushPromises();
    expect(wrapper.find('[role="dialog"]').text()).toContain("reviewed-DESERT-v1"); expect(button(wrapper, "Confirm rights and approve").attributes("disabled")).toBeDefined();
    await wrapper.find('[role="dialog"] input[type="checkbox"]').setValue(true); await button(wrapper, "Confirm rights and approve").trigger("click"); await flushPromises();
    expect(approveCategoryCover).toHaveBeenCalledExactlyOnceWith("secret", "DESERT", "reviewed-DESERT-v1"); expect(wrapper.text()).toContain("Category photo rights approved for the reviewed revision."); wrapper.unmount();
  });
  it("never enables rights approval for missing cover or unsigned English description", async () => {
    state = state.map(row => row.category === "DESERT" ? { ...row, assetId: null, path: null, width: null, height: null, alt: {} } : row);
    const wrapper = make(); await flushPromises(); expect(wrapper.text()).toContain("No cover photo uploaded"); expect(button(wrapper, "Review category photo rights").attributes("disabled")).toBeDefined(); expect(approveCategoryCover).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("uploads once with category revision and UUID, preserves draft rights and verifies readback", async () => {
    const file = new File(["synthetic JPEG"], "owner-category.jpg", { type: "image/jpeg" });
    vi.mocked(uploadCategoryCover).mockImplementation(async (_token, category, _file, _revision, _requestId, alt) => {
      state = state.map(row => row.category === category ? { ...row, alt, revision: "uploaded-v2", rightsStatus: "DRAFT" } : row); return { assetId, mediaId: null };
    });
    const wrapper = make(); await flushPromises(); const input = wrapper.find("#category-file"); Object.defineProperty(input.element, "files", { configurable: true, value: [file] });
    await input.trigger("change"); await wrapper.findAll("form")[1]!.trigger("submit"); await flushPromises();
    expect(uploadCategoryCover).toHaveBeenCalledWith("secret", "DESERT", file, "reviewed-DESERT-v1", expect.stringMatching(/^[0-9a-f-]{36}$/), { en: "Verified DESERT photo" }); expect(uploadCategoryCover).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("Category cover uploaded as an unpublished draft and verified."); expect(approveCategoryCover).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("rejects unsupported files and blocks blind retry after an uncertain upload", async () => {
    const wrapper = make(); await flushPromises(); const input = wrapper.find("#category-file");
    Object.defineProperty(input.element, "files", { configurable: true, value: [new File(["<svg>"], "fake.jpg", { type: "image/svg+xml" })] }); await input.trigger("change"); expect(wrapper.text()).toContain("Choose a non-empty JPEG/PNG"); expect(button(wrapper, "Upload photo").attributes("disabled")).toBeDefined();
    Object.defineProperty(input.element, "files", { configurable: true, value: [new File(["JPEG"], "valid.jpg", { type: "image/jpeg" })] }); await input.trigger("change");
    vi.mocked(uploadCategoryCover).mockRejectedValueOnce(new TypeError("connection lost")); await wrapper.findAll("form")[1]!.trigger("submit"); await flushPromises();
    expect(uploadCategoryCover).toHaveBeenCalledTimes(1); expect(wrapper.text()).toContain("Upload result could not be verified"); expect(button(wrapper, "Upload photo").attributes("disabled")).toBeDefined(); wrapper.unmount();
  });
  it("protects selected file and alt edits against navigation and preserves file selection across categories", async () => {
    const wrapper = make(); await flushPromises(); const file = new File(["JPEG"], "remember-owner-photo.jpg", { type: "image/jpeg" });
    const input = wrapper.find("#category-file"); Object.defineProperty(input.element, "files", { configurable: true, value: [file] }); await input.trigger("change");
    await button(wrapper, "Sea").trigger("click"); await button(wrapper, "Desert").trigger("click"); expect(wrapper.text()).toContain("remember-owner-photo.jpg");
    const confirm = vi.fn(() => false); vi.stubGlobal("confirm", confirm); expect(registerLeave.mock.calls[0]?.[0]()).toBe(false); expect(confirm).toHaveBeenCalled(); wrapper.unmount();
  });
  it("renders Arabic UX and shows readonly category records without upload/manage permissions", async () => {
    vi.stubGlobal("useCookie", () => ref("ar")); writeAuthSession({ token: "secret", email: "fixture@example.invalid", roles: [], permissions: ["tours-operator.tour:view"] });
    const wrapper = make(); await flushPromises(); expect(wrapper.text()).toContain("أغلفة الفئات"); expect(wrapper.text()).toContain("الصحراء"); expect(wrapper.find("fieldset").attributes("disabled")).toBeDefined(); expect(wrapper.find("#category-file").exists()).toBe(false); expect(wrapper.find('[role="dialog"]').exists()).toBe(false); wrapper.unmount();
  });
  it("does not access APIs without tour-view permission or invent unavailable category records", async () => {
    writeAuthSession({ token: "secret", email: "fixture@example.invalid", roles: [], permissions: ["tours-operator.tour:manage"] });
    const denied = make(); await flushPromises(); expect(listCategoryMedia).not.toHaveBeenCalled(); denied.unmount();
    writeAuthSession({ token: "secret", email: "fixture@example.invalid", roles: [], permissions }); state = [];
    const unavailable = make(); await flushPromises(); expect(unavailable.text()).toContain("category media record is unavailable"); expect(unavailable.find("#category-file").exists()).toBe(false); unavailable.unmount();
  });
});
