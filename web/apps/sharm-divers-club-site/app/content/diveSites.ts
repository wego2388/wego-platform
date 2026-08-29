import type { SdcLocale } from "./locales";
import { offerings } from "./offerings";
import type { OfferingSummary } from "./offerings";

export interface DiveSite {
  slug: string;
  name: Record<SdcLocale, string>;
  /**
   * General public geography/history about the location itself — not a
   * Sharm Divers Club business claim, so it isn't gated by approved-facts.json.
   * No invented depth, visibility or marine-life detail: only what's
   * verifiable, well-documented public knowledge about the place. Source
   * and reviewer recorded in DIVE_SITE_SOURCES.md — update that file
   * alongside any change here.
   */
  blurb: Record<SdcLocale, string>;
  /** Codes from offerings.ts whose real, approved name names this site. */
  offeringCodes: string[];
}

export const diveSites: DiveSite[] = [
  {
    slug: "ras-mohammed",
    name: { en: "Ras Mohammed", ar: "رأس محمد" },
    blurb: {
      en: "Ras Mohammed National Park sits at the southern tip of the Sinai Peninsula, where the Gulf of Suez meets the Gulf of Aqaba.",
      ar: "محمية رأس محمد الطبيعية تقع في أقصى جنوب شبه جزيرة سيناء، حيث يلتقي خليج السويس بخليج العقبة.",
    },
    offeringCodes: ["BD02"],
  },
  {
    slug: "tiran",
    name: { en: "Tiran", ar: "تيران" },
    blurb: {
      en: "Tiran Island sits in the Strait of Tiran, between the Sinai Peninsula and Saudi Arabia, at the mouth of the Gulf of Aqaba.",
      ar: "جزيرة تيران تقع في مضيق تيران، بين شبه جزيرة سيناء والسعودية، عند مدخل خليج العقبة.",
    },
    offeringCodes: ["BD08"],
  },
  {
    slug: "thistlegorm",
    name: { en: "SS Thistlegorm", ar: "SS Thistlegorm" },
    blurb: {
      en: "SS Thistlegorm is a British WWII cargo ship that sank in the northern Red Sea in 1941 — one of the world's best-known wreck dives.",
      ar: "SS Thistlegorm سفينة شحن بريطانية غرقت في شمال البحر الأحمر عام 1941 خلال الحرب العالمية الثانية — من أشهر مواقع غوص الحطام في العالم.",
    },
    offeringCodes: ["WC01"],
  },
  {
    slug: "dahab-blue-hole-canyon",
    name: { en: "Dahab Blue Hole & Canyon", ar: "دهب Blue Hole وCanyon" },
    blurb: {
      en: "The Blue Hole and Canyon are well-known dive sites on Sinai's Gulf of Aqaba coast, near Dahab.",
      ar: "Blue Hole وCanyon من أشهر مواقع الغوص على ساحل خليج العقبة في سيناء، بالقرب من دهب.",
    },
    offeringCodes: ["WC02"],
  },
];

export function findDiveSite(slug: string): DiveSite | undefined {
  return diveSites.find(site => site.slug === slug);
}

export function offeringsForSite(site: DiveSite): OfferingSummary[] {
  return offerings.filter(offering => site.offeringCodes.includes(offering.code));
}
