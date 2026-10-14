import type { PublicCategoryCover, PublicTourContent, TourCategory } from "@wego/api-contract";
import type { StsLocale as SiteLocale } from "../content/locales";
import images from "../content/curatedMedia.json";

type Media = PublicTourContent["media"];

/** Owner-approved site defaults, not inventory. Call only for API-resolved tours. */
export function curatedTourMedia(slug: string, locale: SiteLocale): Media {
  return images.filter(image => image.tour === slug).map(image => ({
    path: image.path, width: image.width, height: image.height,
    isCover: image.isCover, alt: image.alt[locale],
  }));
}

/** Published media leads. An initial single uploaded cover may use curated extras. */
export function resolveTourMedia(slug: string, locale: SiteLocale, managed?: Media | null): Media {
  if (!managed?.length) return curatedTourMedia(slug, locale);
  if (managed.length > 1) return managed;
  return [...managed, ...curatedTourMedia(slug, locale).map(image => ({ ...image, isCover: false }))];
}

export function curatedCover(slug: string, locale: SiteLocale) {
  return curatedTourMedia(slug, locale).find(image => image.isCover);
}

const categoryTours: Record<TourCategory, string> = {
  DESERT: "sunset-quad-bike", SEA: "ras-mohamed-white-island-boat", CULTURAL: "cairo-by-bus",
  SHOWS: "dolphin-show", TRANSFERS: "sharm-airport-transfer",
};

export function curatedCategoryCovers(locale: SiteLocale): Partial<Record<TourCategory, PublicCategoryCover>> {
  return Object.fromEntries(Object.entries(categoryTours).map(([category, slug]) => [category, { ...curatedCover(slug, locale)!, category }]));
}

const responsiveImages = new Map(images.map(image => [image.path, image.variants.map(variant => `${variant.path} ${variant.width}w`).join(", ")]));
/** Pre-generated derivatives avoid first-request image processing and upscaling. */
export function curatedSrcset(path: string): string | undefined {
  return responsiveImages.get(path);
}
