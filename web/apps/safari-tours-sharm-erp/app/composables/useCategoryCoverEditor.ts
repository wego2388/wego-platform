import { computed, reactive, ref, shallowRef } from "vue";
import type { CategoryMedia, TourCategory } from "@wego/api-contract";
import { ToursApiError } from "./useToursApi";
import type { PhotoAlt } from "./useTourMediaApi";
import { CONTENT_LOCALES } from "../utils/tourContentEditor";

export const COVER_CATEGORIES: TourCategory[] = ["DESERT", "SEA", "CULTURAL", "SHOWS", "TRANSFERS"];
export interface CategoryCoverApi {
  list(): Promise<CategoryMedia[]>;
  read(category: TourCategory): Promise<CategoryMedia>;
  save(category: TourCategory, revision: string, alt: PhotoAlt): Promise<void>;
  upload(category: TourCategory, file: File, revision: string, requestId: string, alt: PhotoAlt): Promise<void>;
  approve(category: TourCategory, revision: string): Promise<void>;
}
const copyAlt = (alt: PhotoAlt): PhotoAlt => Object.fromEntries(CONTENT_LOCALES.filter(language => alt[language] !== undefined).map(language => [language, alt[language]]));
const fingerprint = (alt: PhotoAlt) => JSON.stringify(copyAlt(alt));
const cleanAlt = (alt: PhotoAlt): PhotoAlt => Object.fromEntries(CONTENT_LOCALES.flatMap(language => alt[language]?.trim() ? [[language, alt[language]!.trim()]] : []));
export const validCategoryAlt = (alt: PhotoAlt) => Object.values(alt).every(text => text.trim().length <= 200);
const known = (category: unknown): category is TourCategory => typeof category === "string" && COVER_CATEGORIES.includes(category as TourCategory);
function validate(rows: CategoryMedia[]) {
  if (!Array.isArray(rows) || rows.some(row => !known(row.category) || typeof row.revision !== "string" || !row.revision || !row.alt || typeof row.alt !== "object"
    || Object.entries(row.alt).some(([language, value]) => !CONTENT_LOCALES.includes(language as typeof CONTENT_LOCALES[number]) || typeof value !== "string")
    || !["DRAFT", "APPROVED"].includes(row.rightsStatus)
    || (row.width !== null && (!Number.isInteger(row.width) || row.width <= 0))
    || (row.height !== null && (!Number.isInteger(row.height) || row.height <= 0)))
    || new Set(rows.map(row => row.category)).size !== rows.length) throw new ToursApiError(502, "invalid_category_response");
}

/** Server category identity/revision is retained independently of unsaved localized alt. */
export function useCategoryCoverEditor(api: CategoryCoverApi) {
  const rows = shallowRef<CategoryMedia[]>([]);
  const drafts = reactive<Record<TourCategory, PhotoAlt>>({ DESERT: {}, SEA: {}, CULTURAL: {}, SHOWS: {}, TRANSFERS: {} });
  const baselines = reactive<Record<TourCategory, string>>({ DESERT: "{}", SEA: "{}", CULTURAL: "{}", SHOWS: "{}", TRANSFERS: "{}" });
  const revisions = reactive<Record<TourCategory, string>>({ DESERT: "", SEA: "", CULTURAL: "", SHOWS: "", TRANSFERS: "" });
  const conflicts = reactive<Partial<Record<TourCategory, boolean>>>({});
  const loading = ref(false);
  const pending = ref<TourCategory | null>(null);
  const verified = ref(false);
  const error = shallowRef<unknown>(null);
  const notice = ref<{ category: TourCategory; action: "saved" | "uploaded" | "approved" } | null>(null);
  let version = 0; let disposed = false;
  const dirty = (category: TourCategory) => fingerprint(drafts[category]) !== baselines[category];
  const row = (category: TourCategory) => rows.value.find(item => item.category === category);
  const hasUnsaved = computed(() => COVER_CATEGORIES.some(dirty));

  function apply(result: CategoryMedia[], saved?: { category: TourCategory; fingerprint: string }) {
    validate(result);
    for (const item of result) {
      const category = item.category;
      const preserve = dirty(category) && !(saved?.category === category && saved.fingerprint === fingerprint(drafts[category]));
      if (preserve) {
        if (item.revision !== revisions[category]) conflicts[category] = true;
      } else {
        drafts[category] = copyAlt(item.alt); baselines[category] = fingerprint(drafts[category]); revisions[category] = item.revision; conflicts[category] = false;
      }
    }
    rows.value = COVER_CATEGORIES.flatMap(category => result.filter(item => item.category === category));
    verified.value = true;
  }

  async function load(): Promise<boolean> {
    if (pending.value || disposed) return false;
    const current = ++version; loading.value = true; verified.value = false; error.value = null; notice.value = null;
    try {
      const result = await api.list(); if (disposed || current !== version) return false;
      apply(result); return true;
    } catch (cause) { if (!disposed && current === version) error.value = cause; return false; }
    finally { if (!disposed && current === version) loading.value = false; }
  }

  async function command(category: TourCategory, action: "saved" | "uploaded" | "approved", mutation: () => Promise<void>, saved?: string): Promise<boolean> {
    if (pending.value || loading.value || disposed || !verified.value || conflicts[category] || !row(category)) return false;
    const current = ++version; pending.value = category; verified.value = false; error.value = null; notice.value = null;
    try {
      await mutation();
      const result = await api.read(category);
      if (disposed || current !== version) return false;
      if (result.category !== category) throw new ToursApiError(502, "invalid_category_response");
      apply(rows.value.map(item => item.category === category ? result : item), saved === undefined ? undefined : { category, fingerprint: saved });
      notice.value = { category, action }; return true;
    } catch (cause) {
      if (disposed || current !== version) return false;
      try {
        const result = await api.read(category);
        if (!disposed && current === version && result.category === category) apply(rows.value.map(item => item.category === category ? result : item));
      } catch { /* Unknown state keeps commands disabled; no success notice. */ }
      if (!disposed && current === version) error.value = cause;
      return false;
    } finally { if (!disposed && current === version) pending.value = null; }
  }

  function save(category: TourCategory) {
    if (!dirty(category) || !validCategoryAlt(drafts[category])) return Promise.resolve(false);
    const snapshot = cleanAlt(drafts[category]); const original = fingerprint(drafts[category]); const revision = revisions[category];
    return command(category, "saved", () => api.save(category, revision, snapshot), original);
  }
  function upload(category: TourCategory, file: File, requestId: string) {
    if (!validCategoryAlt(drafts[category])) return Promise.resolve(false);
    const snapshot = cleanAlt(drafts[category]); const original = fingerprint(drafts[category]); const revision = revisions[category];
    return command(category, "uploaded", () => api.upload(category, file, revision, requestId, snapshot), original);
  }
  function approve(category: TourCategory, revision: string) {
    const item = row(category);
    if (dirty(category) || !item?.assetId || item.revision !== revision || !item.alt.en?.trim()) return Promise.resolve(false);
    return command(category, "approved", () => api.approve(category, revision));
  }
  function discard(category: TourCategory) {
    const item = row(category); if (!item) return;
    drafts[category] = copyAlt(item.alt); baselines[category] = fingerprint(drafts[category]); revisions[category] = item.revision; conflicts[category] = false;
  }
  function dispose() { disposed = true; version++; }
  return { rows, drafts, conflicts, loading, pending, verified, error, notice, dirty, row, hasUnsaved, load, save, upload, approve, discard, dispose };
}
