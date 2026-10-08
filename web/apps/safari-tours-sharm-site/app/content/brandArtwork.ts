import type { TourCategory } from "@wego/api-contract";
import type { StsLocale } from "./locales";

/** Owner-requested AI destination artwork, never a tour's documentary photo. */
export const brandArtwork = {
  hero: { name: "sinai-coast-v1", width: 1672, height: 941 },
  desert: { name: "desert-safari-v1", width: 1536, height: 1024 },
  sea: { name: "red-sea-v1", width: 1536, height: 1024 },
} as const;
export type BrandArtworkKind = keyof typeof brandArtwork;

export const brandArtworkCopy: Record<StsLocale, string> = {
  en: "AI-created destination artwork",
  ar: "صورة تعبيرية مولّدة بالذكاء الاصطناعي",
  ru: "Иллюстрация направления, созданная ИИ",
  it: "Illustrazione della destinazione creata con IA",
};

export function categoryArtwork(category: TourCategory): BrandArtworkKind | null {
  return category === "DESERT" ? "desert" : category === "SEA" ? "sea" : null;
}

export function artworkSource(kind: BrandArtworkKind, width: 480 | 960 | 1600): string {
  return `/images/brand/${brandArtwork[kind].name}-${width}.webp`;
}
