/** One product identity in each browser tab, while retaining distinct SEO copy. */
export function brandTitle(title: string | undefined, locale: string): string {
  const brand = locale === "ar" ? "سفاري تورز شرم" : "Safari Tours Sharm";
  const page = (title ?? "").replace(/(?:\s*[—·|]\s*)?Safari Tours Sharm\s*$/i, "").trim();
  return page ? `${brand} — ${page}` : brand;
}
