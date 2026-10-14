import type { PublicTourContent, TourCategory } from "@wego/api-contract";
import type { CatalogTour } from "../composables/useCatalog";
import { CATEGORY_ORDER } from "../composables/useCatalog";
import type { StsLocale } from "../content/locales";
import { curatedCover } from "./curatedMedia";

export interface GalleryItem {
  entry: CatalogTour;
  cover: NonNullable<PublicTourContent["media"]>[number];
}

export interface GallerySection {
  category: TourCategory;
  items: GalleryItem[];
}

/** Only API-returned active tours get a card; no static page for inactive catalog records. */
export function gallerySections(catalog: CatalogTour[], locale: StsLocale): GallerySection[] {
  return CATEGORY_ORDER.map(category => ({
    category,
    items: catalog
      .filter(entry => entry.tour.isActive && entry.tour.category === category)
      .map(entry => ({ entry, cover: entry.tour.cover ?? curatedCover(entry.tour.slug, locale) }))
      .filter((item): item is GalleryItem => item.cover != null)
      .slice(0, 4),
  })).filter(section => section.items.length > 0);
}
