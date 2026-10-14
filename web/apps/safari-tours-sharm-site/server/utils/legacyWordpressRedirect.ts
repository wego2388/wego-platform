/**
 * Verified one-to-one replacements for historical WordPress pages. These are
 * deliberately fixed to the English canonical, not a visitor cookie: a 301
 * must have the same target for crawlers and every visitor. Never copy the
 * old query string (it may contain PII or obsolete WordPress parameters).
 * See clients/safari-tours-sharm/marketing/LEGACY_URL_DECISIONS_2026-10-14.md.
 */
export const LEGACY_WORDPRESS_REDIRECTS = {
  "/about-us": "/en/about",
  "/cancellation-policy": "/en/terms#section-4",
  "/safari-desert-adventures": "/en/category/desert",
  "/sea-water-activities": "/en/category/sea",
  "/cultural-historical": "/en/category/cultural",
  "/shows-relax-trips": "/en/category/shows",
  "/contact": "/en/contact",
  "/gallery": "/en/gallery",
} as const;

export function legacyWordpressRedirectTarget(path: string): string | null {
  const normalized = path.endsWith("/") ? path.slice(0, -1) : path;
  return LEGACY_WORDPRESS_REDIRECTS[normalized as keyof typeof LEGACY_WORDPRESS_REDIRECTS] ?? null;
}
