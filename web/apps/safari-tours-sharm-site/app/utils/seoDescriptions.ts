import type { InfoCopy } from "../content/info";
import { sentences } from "./structuredData";

/** Meta descriptions built only from the wording already on each page. */
export function termsDescription(terms: InfoCopy["terms"]): string {
  return `${terms.sections[0]!.body[0]} ${sentences(terms.sections[3]!.body[0]!)[0]}`;
}

export function privacyDescription(privacy: InfoCopy["privacy"]): string {
  return `${privacy.sections[0]!.body[0]} ${sentences(privacy.sections[1]!.body[0]!).at(-1)}`;
}

export function faqDescription(faq: InfoCopy["faq"], contact: InfoCopy["contact"]): string {
  return `${faq.intro} ${contact.intro}`;
}
