export const SITE_LOCALES = ["en", "ar", "ru", "it"] as const;

/**
 * Public route roots that existed before locale prefixes. Only these are
 * redirected, so junk URLs (/wp-admin, typos) get a straight 404 instead of a
 * redirect hop, and a wrongly-cased locale (/EN/tours) is normalised.
 */
const LEGACY_ROOTS = ["tours", "tour", "category", "booking", "my-booking", "contact", "privacy", "terms", "about", "faq", "trip-finder", "design-system"];

/**
 * Where a language-less URL should go, or null when it needs no redirect.
 * Keeps the fixed Paymob return URL, bookmarks and shared links working after
 * the move to /{locale}/… routes; the query string is preserved. The target
 * always starts with "/{locale}/", so it can never become protocol-relative.
 */
export function localeRedirectTarget(path: string, search: string, rememberedLocale: string | undefined): string | null {
  const segments = path.split("/");
  const first = (segments[1] ?? "").toLowerCase();
  const locale =
    rememberedLocale && (SITE_LOCALES as readonly string[]).includes(rememberedLocale) ? rememberedLocale : "en";
  if ((SITE_LOCALES as readonly string[]).includes(first)) {
    // Correct case already: nothing to do. Wrong case (/EN/…): normalise.
    return segments[1] === first ? null : `/${first}/${segments.slice(2).join("/")}${search}`;
  }
  if (!LEGACY_ROOTS.includes(first)) return null;
  return `/${locale}${path}${search}`;
}
