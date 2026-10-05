import { describe, expect, it, vi } from "vitest";
import type { CategoryMedia, TourCategory } from "@wego/api-contract";
import { COVER_CATEGORIES, useCategoryCoverEditor, validCategoryAlt, type CategoryCoverApi } from "../app/composables/useCategoryCoverEditor";
import { ToursApiError } from "../app/composables/useToursApi";
import { categoryAr, categoryEn } from "../app/utils/categoryMessages";

const item = (category: TourCategory = "DESERT", revision = "original-v1", en = "Verified real photo"): CategoryMedia => ({ category, revision, alt: { en }, assetId: null, path: null, width: null, height: null, rightsStatus: "DRAFT", approvedAt: null, updatedAt: "2026-10-05T10:00:00Z" });
const withAsset = (): CategoryMedia => ({ ...item(), assetId: "16f34404-0bc9-4cf0-ae8b-3c60f7d5f7d0", path: "/media/categories/DESERT/16f34404-0bc9-4cf0-ae8b-3c60f7d5f7d0.jpg", width: 600, height: 400 });
function api(state: () => CategoryMedia[] = () => COVER_CATEGORIES.map(category => item(category))): CategoryCoverApi {
  return { list: vi.fn(async () => state()), read: vi.fn(async category => state().find(row => row.category === category)!), save: vi.fn(async () => {}), upload: vi.fn(async () => {}), approve: vi.fn(async () => {}) };
}
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(yes => { resolve = yes; }); return { resolve, promise }; }
describe("safe five-category cover editor", () => {
  it("keeps only known existing categories, does not create missing records or fabricate cover facts", async () => {
    const editor = useCategoryCoverEditor(api(() => [item("SEA")])); await editor.load();
    expect(editor.rows.value.map(row => row.category)).toEqual(["SEA"]); expect(editor.row("DESERT")).toBeUndefined(); expect(editor.drafts.DESERT).toEqual({});
    expect(editor.hasUnsaved.value).toBe(false); expect(await editor.upload("DESERT", new File(["bytes"], "image.jpg"), "request-id")).toBe(false);
  });
  it("preserves four-language edits independently across categories and harmless refreshes", async () => {
    const editor = useCategoryCoverEditor(api()); await editor.load();
    editor.drafts.DESERT = { en: "Local English", ar: "نص عربي", ru: "Русский", it: "Italiano" }; editor.drafts.SEA.ar = "نص البحر";
    await editor.load(); expect(editor.drafts.DESERT.ru).toBe("Русский"); expect(editor.drafts.SEA.ar).toBe("نص البحر"); expect(editor.hasUnsaved.value).toBe(true); expect(editor.conflicts.DESERT).toBe(false);
  });
  it("preserves stale local text and blocks writes after a concurrent revision until explicit discard", async () => {
    let state = [item()]; const dependency = api(() => state); const editor = useCategoryCoverEditor(dependency); await editor.load();
    editor.drafts.DESERT.en = "Keep unsaved text"; state = [item("DESERT", "remote-v2", "Concurrent approved text")]; await editor.load();
    expect(editor.conflicts.DESERT).toBe(true); expect(editor.drafts.DESERT.en).toBe("Keep unsaved text"); expect(await editor.save("DESERT")).toBe(false); expect(dependency.save).not.toHaveBeenCalled();
    editor.discard("DESERT"); expect(editor.conflicts.DESERT).toBe(false); expect(editor.drafts.DESERT.en).toBe("Concurrent approved text"); expect(editor.dirty("DESERT")).toBe(false);
  });
  it("saves only reviewed category revision and keeps another category's unsaved edits", async () => {
    let state = [item(), item("SEA")]; const dependency = api(() => state);
    vi.mocked(dependency.save).mockImplementation(async (category, _revision, alt) => { state = state.map(row => row.category === category ? { ...row, alt, revision: "saved-v2" } : row); });
    const editor = useCategoryCoverEditor(dependency); await editor.load(); editor.drafts.DESERT.en = "Owner-approved changed alt"; editor.drafts.SEA.ar = "عمل محلي";
    expect(await editor.save("DESERT")).toBe(true); expect(dependency.save).toHaveBeenCalledExactlyOnceWith("DESERT", "original-v1", { en: "Owner-approved changed alt" });
    expect(editor.dirty("DESERT")).toBe(false); expect(editor.drafts.SEA.ar).toBe("عمل محلي"); expect(editor.notice.value).toEqual({ category: "DESERT", action: "saved" }); expect(dependency.approve).not.toHaveBeenCalled();
  });
  it("uploads draft with UUID/revision and selected local descriptions without approving rights", async () => {
    let state = [item()]; const dependency = api(() => state);
    vi.mocked(dependency.upload).mockImplementation(async (_category, _file, _revision, _requestId, alt) => { state = [{ ...withAsset(), alt, revision: "uploaded-v2" }]; });
    const editor = useCategoryCoverEditor(dependency); await editor.load(); editor.drafts.DESERT.ar = "صورة حقيقية";
    const file = new File(["JPEG"], "photo.jpg", { type: "image/jpeg" }); expect(await editor.upload("DESERT", file, "same-attempt-uuid")).toBe(true);
    expect(dependency.upload).toHaveBeenCalledExactlyOnceWith("DESERT", file, "original-v1", "same-attempt-uuid", { en: "Verified real photo", ar: "صورة حقيقية" });
    expect(editor.row("DESERT")?.rightsStatus).toBe("DRAFT"); expect(dependency.approve).not.toHaveBeenCalled();
  });
  it("refuses approval for dirty, missing asset, absent English or unreviewed revision", async () => {
    let state = [item()]; const dependency = api(() => state); const editor = useCategoryCoverEditor(dependency); await editor.load();
    expect(await editor.approve("DESERT", "original-v1")).toBe(false); state = [withAsset()]; await editor.load();
    expect(await editor.approve("DESERT", "unseen")).toBe(false); editor.drafts.DESERT.ar = "غير محفوظ"; expect(await editor.approve("DESERT", "original-v1")).toBe(false); expect(dependency.approve).not.toHaveBeenCalled();
  });
  it("uses exact reviewed revision and never approves while another operation is pending", async () => {
    const dependency = api(() => [withAsset()]); const hold = deferred<undefined>(); vi.mocked(dependency.approve).mockImplementation(async () => hold.promise);
    const editor = useCategoryCoverEditor(dependency); await editor.load(); const approving = editor.approve("DESERT", "original-v1");
    expect(await editor.approve("DESERT", "original-v1")).toBe(false); expect(await editor.load()).toBe(false); hold.resolve(undefined); expect(await approving).toBe(true);
    expect(dependency.approve).toHaveBeenCalledExactlyOnceWith("DESERT", "original-v1");
  });
  it("refreshes a rejected revision without losing local text, retrying writes or claiming success", async () => {
    let state = [item()]; const dependency = api(() => state);
    vi.mocked(dependency.save).mockImplementation(async () => { state = [item("DESERT", "remote-v2", "Different saved text")]; throw new ToursApiError(409, "draft_changed"); });
    const editor = useCategoryCoverEditor(dependency); await editor.load(); editor.drafts.DESERT.en = "Preserve local text";
    expect(await editor.save("DESERT")).toBe(false); expect(editor.drafts.DESERT.en).toBe("Preserve local text"); expect(editor.conflicts.DESERT).toBe(true); expect(editor.notice.value).toBeNull();
    expect(dependency.save).toHaveBeenCalledTimes(1); expect(dependency.read).toHaveBeenCalledTimes(1);
  });
  it("does not claim a verified mutation if subsequent reads fail, and keeps local input", async () => {
    const dependency = api(); const editor = useCategoryCoverEditor(dependency); await editor.load(); editor.drafts.DESERT.en = "Keep local";
    vi.mocked(dependency.read).mockRejectedValue(new Error("private diagnostic"));
    expect(await editor.save("DESERT")).toBe(false); expect(editor.verified.value).toBe(false); expect(editor.notice.value).toBeNull(); expect(editor.drafts.DESERT.en).toBe("Keep local");
    expect(await editor.save("DESERT")).toBe(false); expect(dependency.save).toHaveBeenCalledTimes(1);
  });
  it("ignores stale load results and disposed responses", async () => {
    const first = deferred<CategoryMedia[]>(); const second = deferred<CategoryMedia[]>(); const dependency = api();
    vi.mocked(dependency.list).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const editor = useCategoryCoverEditor(dependency); const a = editor.load(); const b = editor.load(); second.resolve([item("SEA", "new")]); await b; first.resolve([item("DESERT", "old")]); await a;
    expect(editor.rows.value.map(row => row.category)).toEqual(["SEA"]);
    const late = deferred<CategoryMedia[]>(); vi.mocked(dependency.list).mockReturnValueOnce(late.promise); const loading = editor.load(); editor.dispose(); late.resolve([item()]); await loading; expect(editor.rows.value.map(row => row.category)).toEqual(["SEA"]);
  });
  it("rejects unknown or duplicate categories and invalid payload instead of inventing replacement records", async () => {
    for (const rows of [[item(), item()], [{ ...item(), category: "INVENTED" }], [{ ...item(), revision: "" }], [{ ...item(), alt: { en: 123 } }]]) {
      const editor = useCategoryCoverEditor(api(() => rows as CategoryMedia[])); expect(await editor.load()).toBe(false); expect(editor.rows.value).toEqual([]); expect(editor.verified.value).toBe(false);
    }
  });
  it("keeps every EN/AR label present, with bounded descriptions", () => {
    expect(Object.keys(categoryAr).sort()).toEqual(Object.keys(categoryEn).sort()); expect(Object.values(categoryAr).every(text => text.trim())).toBe(true);
    expect(validCategoryAlt({})).toBe(true); expect(validCategoryAlt({ en: "x".repeat(201) })).toBe(false);
  });
});
