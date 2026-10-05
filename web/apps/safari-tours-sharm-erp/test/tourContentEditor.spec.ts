import { describe, expect, it, vi } from "vitest";
import type { StaffTourContent, TourContentDocument, TourFactsDocument } from "@wego/api-contract";
import { useTourContentEditor, type TourContentEditorApi } from "../app/composables/useTourContentEditor";
import { ToursApiError } from "../app/composables/useToursApi";
import { contentEn, contentAr, contentErrorKey } from "../app/utils/contentMessages";
import { contentIsValid, factsAreValid, normalizeContent, normalizeFacts, splitContentLines } from "../app/utils/tourContentEditor";

const document = (name = "Owner-approved title"): TourContentDocument => normalizeContent({ name, shortDescription: "Approved summary", description: "Approved factual description", stops: [{ stopKey: "meeting", name: "Approved meeting", description: null }] });
function fixture(revision = "reviewed-v1", title = "Owner-approved title"): StaffTourContent {
  return { content: { en: [{ stage: "DRAFT", revision, updatedAt: "2026-10-05T10:00:00Z", document: document(title) }] }, facts: [{ stage: "DRAFT", revision: "facts-v1", updatedAt: "2026-10-05T10:00:00Z", document: normalizeFacts() }], media: [], mediaRevision: "whole-list-v1" };
}
function api(read: () => Promise<StaffTourContent> = async () => fixture()): TourContentEditorApi {
  return { read: vi.fn(read), save: vi.fn(async () => {}), publish: vi.fn(async () => {}), unpublish: vi.fn(async () => {}), saveFacts: vi.fn(async () => {}), publishFacts: vi.fn(async () => {}), unpublishFacts: vi.fn(async () => {}) };
}
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: unknown) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }

describe("four-locale safe content editor", () => {
  it("loads existing drafts without inventing missing translations or shared facts", async () => {
    const editor = useTourContentEditor(api());
    expect(await editor.load()).toBe(true);
    expect(editor.documents.en.name).toBe("Owner-approved title");
    expect(editor.documents.ar.name).toBe("");
    expect(editor.facts.value.childrenAllowed).toBeNull();
    expect(editor.hasUnsavedChanges.value).toBe(false);
    editor.documents.en.name = "Local English change";
    editor.documents.ar.name = "تعديل محلي";
    expect(editor.documents.en.name).toBe("Local English change");
    expect(editor.dirty("en")).toBe(true);
    expect(editor.dirty("ar")).toBe(true);
  });

  it("refreshes saved state while preserving local edits in every locale and facts", async () => {
    let state = fixture();
    const editor = useTourContentEditor(api(async () => state));
    await editor.load();
    editor.documents.en.description = "Keep this local unsaved description";
    editor.documents.ru.name = "Локальное изменение";
    editor.facts.value.childrenAllowed = false;
    state = fixture("remote-v2", "New server title");
    await editor.load();
    expect(editor.documents.en.description).toContain("local unsaved");
    expect(editor.documents.ru.name).toBe("Локальное изменение");
    expect(editor.facts.value.childrenAllowed).toBe(false);
    expect(editor.server.value?.content.en?.[0]?.revision).toBe("remote-v2");
    expect(editor.hasUnsavedChanges.value).toBe(true);
  });

  it("ignores out-of-order loads and responses after disposal", async () => {
    const first = deferred<StaffTourContent>();
    const second = deferred<StaffTourContent>();
    const dependency = api();
    vi.mocked(dependency.read).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const editor = useTourContentEditor(dependency);
    const oldLoad = editor.load(); const newLoad = editor.load();
    second.resolve(fixture("new", "Latest")); await newLoad;
    first.resolve(fixture("old", "Stale")); await oldLoad;
    expect(editor.documents.en.name).toBe("Latest");
    const late = deferred<StaffTourContent>();
    vi.mocked(dependency.read).mockReturnValueOnce(late.promise);
    const loading = editor.load(); editor.dispose(); late.resolve(fixture("late", "Do not apply")); await loading;
    expect(editor.documents.en.name).toBe("Latest");
  });

  it("saves one locale without discarding another locale's edits and preserves its stop keys", async () => {
    let state = fixture();
    const dependency = api(async () => state);
    vi.mocked(dependency.save).mockImplementation(async (language, text) => {
      state = { ...state, content: { ...state.content, [language]: [{ stage: "DRAFT", revision: "saved-v2", updatedAt: "2026-10-05T11:00:00Z", document: text }] } };
    });
    const editor = useTourContentEditor(dependency); await editor.load();
    editor.documents.en.name = "Reviewed new title";
    editor.documents.it.description = "Keep Italian work";
    expect(await editor.save("en")).toBe(true);
    expect(dependency.save).toHaveBeenCalledWith("en", expect.objectContaining({ name: "Reviewed new title", stops: [{ stopKey: "meeting", name: "Approved meeting", description: null }] }));
    expect(editor.dirty("en")).toBe(false);
    expect(editor.dirty("it")).toBe(true);
    expect(editor.notice.value).toBe("saved");
  });

  it("does not discard local edits made during an in-flight save", async () => {
    let state = fixture();
    const hold = deferred<undefined>();
    const dependency = api(async () => state);
    vi.mocked(dependency.save).mockImplementation(async (_language, text) => { await hold.promise; state = fixture("saved"); state.content.en![0]!.document = text; });
    const editor = useTourContentEditor(dependency); await editor.load();
    editor.documents.en.name = "Snapshot sent";
    const saving = editor.save("en");
    editor.documents.en.name = "Typed afterwards";
    hold.resolve(undefined); await saving;
    expect(editor.documents.en.name).toBe("Typed afterwards");
    expect(editor.dirty("en")).toBe(true);
  });

  it("refuses publishing dirty, missing or unreviewed revisions", async () => {
    const dependency = api(); const editor = useTourContentEditor(dependency); await editor.load();
    expect(await editor.publish("en", "not-reviewed")).toBe(false);
    expect(await editor.publish("ar", "reviewed-v1")).toBe(false);
    editor.documents.en.name = "Unsaved change";
    expect(await editor.publish("en", "reviewed-v1")).toBe(false);
    expect(dependency.publish).not.toHaveBeenCalled();
  });

  it("uses the exact reviewed revision, with no fabricated publish on save", async () => {
    const dependency = api(); const editor = useTourContentEditor(dependency); await editor.load();
    expect(await editor.publish("en", "reviewed-v1")).toBe(true);
    expect(dependency.publish).toHaveBeenCalledExactlyOnceWith("en", "reviewed-v1");
    expect(dependency.save).not.toHaveBeenCalled();
  });

  it("refreshes draft_changed and requires review again without losing local other-language edits", async () => {
    let state = fixture(); const dependency = api(async () => state);
    vi.mocked(dependency.publish).mockImplementation(async () => { state = fixture("remote-v3", "Concurrent change"); throw new ToursApiError(409, "draft_changed"); });
    const editor = useTourContentEditor(dependency); await editor.load(); editor.documents.ar.description = "Keep Arabic edits";
    expect(await editor.publish("en", "reviewed-v1")).toBe(false);
    expect(editor.documents.en.name).toBe("Concurrent change");
    expect(editor.documents.ar.description).toBe("Keep Arabic edits");
    expect(contentErrorKey(editor.error.value)).toBe("conflict");
    expect(editor.notice.value).toBeNull();
    expect(await editor.publish("en", "reviewed-v1")).toBe(false);
    expect(dependency.publish).toHaveBeenCalledTimes(1);
  });

  it("does not claim success if a mutation or its verification refresh fails", async () => {
    const dependency = api(); const editor = useTourContentEditor(dependency); await editor.load();
    editor.documents.en.name = "Keep unsaved input";
    vi.mocked(dependency.read).mockRejectedValueOnce(new TypeError("network failed with private diagnostics"));
    expect(await editor.save("en")).toBe(false);
    expect(editor.notice.value).toBeNull(); expect(editor.verified.value).toBe(false);
    expect(editor.documents.en.name).toBe("Keep unsaved input");
    expect(await editor.publish("en", "reviewed-v1")).toBe(false);
    expect(await editor.load()).toBe(true);
    expect(editor.documents.en.name).toBe("Keep unsaved input");
  });

  it("prevents duplicate concurrent commands and preserves explicit unknown/false facts", async () => {
    const hold = deferred<undefined>(); const state = fixture(); const dependency = api(async () => state);
    vi.mocked(dependency.saveFacts).mockImplementation(async facts => { await hold.promise; state.facts[0]!.document = facts; });
    const editor = useTourContentEditor(dependency); await editor.load();
    editor.facts.value.childrenAllowed = false;
    const saving = editor.saveFacts();
    expect(await editor.saveFacts()).toBe(false);
    expect(await editor.load()).toBe(false);
    hold.resolve(undefined); expect(await saving).toBe(true);
    expect(dependency.saveFacts).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ childrenAllowed: false, minimumAge: null, hotelPickup: null }));
    expect(editor.factsDirty.value).toBe(false);
  });

  it("unpublishes independently without deleting an unsaved text draft", async () => {
    const dependency = api(); const editor = useTourContentEditor(dependency); await editor.load();
    editor.documents.en.name = "Preserved edit";
    expect(await editor.unpublish("en")).toBe(true);
    expect(editor.documents.en.name).toBe("Preserved edit");
    expect(editor.dirty("en")).toBe(true);
    expect(dependency.save).not.toHaveBeenCalled();
  });
});

describe("content readiness and translations", () => {
  it("has complete EN/AR staff messages and never exposes private exception text", () => {
    expect(Object.keys(contentAr).sort()).toEqual(Object.keys(contentEn).sort());
    expect(Object.values(contentAr).every(value => value.trim().length > 0)).toBe(true);
    expect(contentErrorKey(new Error("private path/credentials"))).toBe("offline");
    expect(contentErrorKey(new ToursApiError(403, "private"))).toBe("forbidden");
    expect(contentErrorKey(new ToursApiError(401, "private"))).toBe("expired");
    expect(contentErrorKey(new ToursApiError(404, "private"))).toBe("notFound");
  });
  it("matches bounded content rules and does not pad blank commercial content", () => {
    expect(contentIsValid(document())).toBe(true); expect(contentIsValid(normalizeContent())).toBe(false);
    for (const invalid of [{ ...document(), name: "x".repeat(121) }, { ...document(), includes: Array(21).fill("Item") }, { ...document(), excludes: ["x".repeat(201)] }, { ...document(), stops: [{ stopKey: "../bad", name: "Name" }] }]) expect(contentIsValid(invalid)).toBe(false);
    expect(splitContentLines(" One \r\n\n Two ")).toEqual(["One", "Two"]);
  });
  it("keeps facts unknown unless supplied and rejects invented blank/default coordinates", () => {
    expect(normalizeFacts()).toEqual({ childrenAllowed: null, minimumAge: null, guideLanguages: [], hotelPickup: null, stops: [] });
    expect(factsAreValid(normalizeFacts())).toBe(true);
    const invalid: TourFactsDocument[] = [{ minimumAge: Number.NaN }, { minimumAge: 1.5 }, { minimumAge: 100 }, { guideLanguages: ["EN"] }, { guideLanguages: ["en", "en"] }, { stops: [{ key: "stop", kind: "STOP", latitude: Number.NaN, longitude: 0 }] }, { stops: [{ key: "stop", kind: "STOP", latitude: 91, longitude: 0 }] }];
    for (const facts of invalid) expect(factsAreValid(facts)).toBe(false);
  });
});
