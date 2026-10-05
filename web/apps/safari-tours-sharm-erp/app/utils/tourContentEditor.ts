import type { ContentLocale, StaffTourContent, TourContentDocument, TourFactsDocument } from "@wego/api-contract";

export const CONTENT_LOCALES: ContentLocale[] = ["en", "ar", "ru", "it"];
export const CONTENT_LANGUAGE_NAMES = { en: "English", ar: "العربية", ru: "Русский", it: "Italiano" } as const;
export type ContentRecord = StaffTourContent["content"][string][number];
export type FactsRecord = StaffTourContent["facts"][number];

/** A blank document means missing owner content, never generated commercial copy. */
export function normalizeContent(document?: TourContentDocument): TourContentDocument {
  return {
    name: document?.name ?? "", shortDescription: document?.shortDescription ?? "", description: document?.description ?? "",
    includes: [...(document?.includes ?? [])], excludes: [...(document?.excludes ?? [])], knowBeforeYouGo: [...(document?.knowBeforeYouGo ?? [])],
    meetingPoint: document?.meetingPoint ?? null,
    stops: (document?.stops ?? []).map(stop => ({ stopKey: stop.stopKey, name: stop.name, description: stop.description ?? null })),
  };
}

export function normalizeFacts(document?: TourFactsDocument): TourFactsDocument {
  return {
    childrenAllowed: document?.childrenAllowed ?? null, minimumAge: document?.minimumAge ?? null,
    guideLanguages: [...(document?.guideLanguages ?? [])], hotelPickup: document?.hotelPickup ?? null,
    stops: (document?.stops ?? []).map(stop => ({ ...stop })),
  };
}

export const documentFingerprint = (document: TourContentDocument | TourFactsDocument) => JSON.stringify(document);
export const contentRecord = (server: StaffTourContent | null, locale: ContentLocale, stage: "DRAFT" | "PUBLISHED") =>
  server?.content[locale]?.find(record => record.stage === stage);
export const factsRecord = (server: StaffTourContent | null, stage: "DRAFT" | "PUBLISHED") => server?.facts.find(record => record.stage === stage);
export const splitContentLines = (text: string) => text.split(/\r?\n/).map(line => line.trim()).filter(Boolean);

/** Keep client limits in step with TourContentDocument; server remains authoritative. */
export function contentIsValid(document: TourContentDocument): boolean {
  if (![document.name, document.shortDescription, document.description].every(value => value.trim().length > 0)) return false;
  if (document.name.trim().length > 120 || document.shortDescription.trim().length > 300 || document.description.trim().length > 6000) return false;
  if ((document.meetingPoint?.trim().length ?? 0) > 300) return false;
  if ([document.includes, document.excludes, document.knowBeforeYouGo].some(items => (items?.length ?? 0) > 20
    || items?.some(item => item.trim().length === 0 || item.trim().length > 200))) return false;
  const stops = document.stops ?? [];
  return stops.length <= 12 && new Set(stops.map(stop => stop.stopKey)).size === stops.length
    && stops.every(stop => /^[a-z0-9][a-z0-9-]{0,39}$/.test(stop.stopKey) && stop.name.trim().length > 0
      && stop.name.trim().length <= 120 && (stop.description?.trim().length ?? 0) <= 500);
}

export function factsAreValid(document: TourFactsDocument): boolean {
  const languages = document.guideLanguages ?? [];
  const stops = document.stops ?? [];
  return (document.minimumAge == null || (Number.isInteger(document.minimumAge) && document.minimumAge >= 0 && document.minimumAge <= 99))
    && languages.length <= 10 && new Set(languages).size === languages.length && languages.every(code => /^[a-z]{2}$/.test(code))
    && stops.length <= 12 && new Set(stops.map(stop => stop.key)).size === stops.length
    && stops.filter(stop => stop.kind === "MEETING_POINT").length <= 1
    && stops.every(stop => /^[a-z0-9][a-z0-9-]{0,39}$/.test(stop.key) && Number.isFinite(stop.latitude) && Number.isFinite(stop.longitude)
      && stop.latitude >= -90 && stop.latitude <= 90 && stop.longitude >= -180 && stop.longitude <= 180);
}
