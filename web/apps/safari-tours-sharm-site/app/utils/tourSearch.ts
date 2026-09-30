/**
 * Instant, dependency-free search over the tour catalogue (≈30 tours). Every
 * query word must prefix-match a word in the tour's text; Arabic is folded
 * (hamza forms, taa marbuta, alef maqsura, tashkeel) and Latin accents are
 * dropped, so "رحله" finds "رحلة" and "citta" finds "città".
 */
export function foldSearchText(value: string): string {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[ً-ٰٟـ]/g, "")
    .replace(/[أإآٱ]/g, "ا")
    .replace(/ة/g, "ه")
    .replace(/ى/g, "ي")
    .replace(/ё/g, "е");
}

export function searchWords(value: string): string[] {
  return foldSearchText(value)
    .split(/[^\p{L}\p{N}]+/u)
    .filter(Boolean);
}

export interface SearchDocument<T> {
  item: T;
  words: string[];
}

export function buildSearchIndex<T>(items: T[], text: (item: T) => (string | null | undefined)[]): SearchDocument<T>[] {
  return items.map((item) => ({
    item,
    words: [...new Set(text(item).filter((part): part is string => Boolean(part)).flatMap(searchWords))],
  }));
}

export function searchIndex<T>(index: SearchDocument<T>[], query: string): T[] {
  const terms = searchWords(query);
  if (terms.length === 0) return index.map((doc) => doc.item);
  return index
    .filter((doc) => terms.every((term) => doc.words.some((word) => word.startsWith(term))))
    .map((doc) => doc.item);
}
