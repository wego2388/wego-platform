import { computed, reactive, ref, shallowRef } from "vue";
import type { ContentLocale, StaffTourContent, TourContentDocument, TourFactsDocument } from "@wego/api-contract";
import { ToursApiError } from "./useToursApi";
import { CONTENT_LOCALES, contentIsValid, contentRecord, documentFingerprint, factsAreValid, factsRecord, normalizeContent, normalizeFacts } from "../utils/tourContentEditor";

export interface TourContentEditorApi {
  read(): Promise<StaffTourContent>;
  save(locale: ContentLocale, document: TourContentDocument): Promise<void>;
  publish(locale: ContentLocale, revision: string): Promise<void>;
  unpublish(locale: ContentLocale): Promise<void>;
  saveFacts(document: TourFactsDocument): Promise<void>;
  publishFacts(revision: string): Promise<void>;
  unpublishFacts(): Promise<void>;
}

/** Locale switching never replaces local edits. Mutations only publish a reviewed server revision. */
export function useTourContentEditor(api: TourContentEditorApi) {
  const server = shallowRef<StaffTourContent | null>(null);
  const documents = reactive<Record<ContentLocale, TourContentDocument>>({ en: normalizeContent(), ar: normalizeContent(), ru: normalizeContent(), it: normalizeContent() });
  const baselines = reactive<Record<ContentLocale, string>>({ en: documentFingerprint(documents.en), ar: documentFingerprint(documents.ar), ru: documentFingerprint(documents.ru), it: documentFingerprint(documents.it) });
  const facts = ref<TourFactsDocument>(normalizeFacts());
  const factsBaseline = ref(documentFingerprint(facts.value));
  const loading = ref(false);
  const pending = ref<string | null>(null);
  const verified = ref(false);
  const error = shallowRef<unknown>(null);
  const notice = ref<"saved" | "published" | "unpublished" | null>(null);
  let version = 0;
  let disposed = false;
  const dirty = (locale: ContentLocale) => documentFingerprint(documents[locale]) !== baselines[locale];
  const factsDirty = computed(() => documentFingerprint(facts.value) !== factsBaseline.value);
  const hasUnsavedChanges = computed(() => CONTENT_LOCALES.some(dirty) || factsDirty.value);

  function apply(result: StaffTourContent, saved?: { locale: ContentLocale; fingerprint: string }, savedFacts?: string) {
    for (const locale of CONTENT_LOCALES) {
      const preserve = dirty(locale) && !(saved?.locale === locale && saved.fingerprint === documentFingerprint(documents[locale]));
      const document = normalizeContent(contentRecord(result, locale, "DRAFT")?.document ?? contentRecord(result, locale, "PUBLISHED")?.document);
      baselines[locale] = documentFingerprint(document);
      if (!preserve) documents[locale] = document;
    }
    const preserveFacts = factsDirty.value && savedFacts !== documentFingerprint(facts.value);
    const document = normalizeFacts(factsRecord(result, "DRAFT")?.document ?? factsRecord(result, "PUBLISHED")?.document);
    factsBaseline.value = documentFingerprint(document);
    if (!preserveFacts) facts.value = document;
    server.value = result;
    verified.value = true;
  }

  async function load(): Promise<boolean> {
    if (pending.value || disposed) return false;
    const current = ++version;
    loading.value = true;
    verified.value = false;
    error.value = null;
    notice.value = null;
    try {
      const result = await api.read();
      if (disposed || current !== version) return false;
      apply(result);
      return true;
    } catch (cause) {
      if (!disposed && current === version) error.value = cause;
      return false;
    } finally {
      if (!disposed && current === version) loading.value = false;
    }
  }

  async function mutate(action: string, command: () => Promise<void>, message: "saved" | "published" | "unpublished", saved?: { locale: ContentLocale; fingerprint: string }, savedFacts?: string): Promise<boolean> {
    if (pending.value || loading.value || disposed || !verified.value) return false;
    const current = ++version;
    pending.value = action;
    verified.value = false;
    error.value = null;
    notice.value = null;
    try {
      await command();
      const result = await api.read();
      if (disposed || current !== version) return false;
      apply(result, saved, savedFacts);
      notice.value = message;
      return true;
    } catch (cause) {
      if (disposed || current !== version) return false;
      if (cause instanceof ToursApiError && cause.errorCode === "draft_changed") {
        try {
          const result = await api.read();
          if (!disposed && current === version) apply(result);
        } catch { /* Keep publication disabled until an explicit successful refresh. */ }
      }
      if (!disposed && current === version) error.value = cause;
      return false;
    } finally {
      if (!disposed && current === version) pending.value = null;
    }
  }

  const save = (locale: ContentLocale) => {
    const snapshot = normalizeContent(documents[locale]);
    if (!contentIsValid(snapshot)) return Promise.resolve(false);
    return mutate(`save:${locale}`, () => api.save(locale, snapshot), "saved", { locale, fingerprint: documentFingerprint(snapshot) });
  };
  const publish = (locale: ContentLocale, reviewedRevision: string) => {
    const draft = contentRecord(server.value, locale, "DRAFT");
    if (dirty(locale) || !draft || draft.revision !== reviewedRevision) return Promise.resolve(false);
    return mutate(`publish:${locale}`, () => api.publish(locale, reviewedRevision), "published");
  };
  const unpublish = (locale: ContentLocale) => mutate(`unpublish:${locale}`, () => api.unpublish(locale), "unpublished");
  const saveFacts = () => {
    const snapshot = normalizeFacts(facts.value);
    if (!factsAreValid(snapshot)) return Promise.resolve(false);
    return mutate("save:facts", () => api.saveFacts(snapshot), "saved", undefined, documentFingerprint(snapshot));
  };
  const publishFacts = (reviewedRevision: string) => {
    const draft = factsRecord(server.value, "DRAFT");
    if (factsDirty.value || !draft || draft.revision !== reviewedRevision) return Promise.resolve(false);
    return mutate("publish:facts", () => api.publishFacts(reviewedRevision), "published");
  };
  const unpublishFacts = () => mutate("unpublish:facts", () => api.unpublishFacts(), "unpublished");
  function dispose() { disposed = true; version++; }
  return { server, documents, facts, loading, pending, verified, error, notice, dirty, factsDirty, hasUnsavedChanges, load, save, publish, unpublish, saveFacts, publishFacts, unpublishFacts, dispose };
}
