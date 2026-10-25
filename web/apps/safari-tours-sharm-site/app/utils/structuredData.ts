import { siteWebsite, facebookUrl, instagramUrl, tripadvisorUrl, whatsappPhone, siteEmail } from "../content/locales";

/** Absolute logo URL (existing brand asset). */
export const logoUrl = `${siteWebsite}/brand/logo-full.webp`;
/** Official profiles already linked in the site footer. */
export const sameAsUrls = [facebookUrl, instagramUrl, tripadvisorUrl];
export const brandName = "Safari Tours Sharm";

export interface Crumb { name: string; path: string }

/** JSON for a <script> tag: staff-written text can never close the tag. */
export function jsonLd(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}

const abs = (path: string) => `${siteWebsite}${path}`;

export function organizationNode(): Record<string, unknown> {
  return {
    "@type": "TravelAgency",
    "@id": `${siteWebsite}/#organization`,
    name: brandName,
    url: siteWebsite,
    logo: logoUrl,
    image: logoUrl,
    email: siteEmail,
    telephone: whatsappPhone,
    areaServed: "Sharm El Sheikh, Egypt",
    currenciesAccepted: "EUR",
    sameAs: sameAsUrls,
  };
}

export function websiteNode(locale: string): Record<string, unknown> {
  return {
    "@type": "WebSite",
    "@id": `${siteWebsite}/#website`,
    name: brandName,
    url: siteWebsite,
    inLanguage: locale,
    publisher: { "@id": `${siteWebsite}/#organization` },
  };
}

export function homeGraph(locale: string): Record<string, unknown> {
  return { "@context": "https://schema.org", "@graph": [organizationNode(), websiteNode(locale)] };
}

export function breadcrumbList(crumbs: Crumb[]): Record<string, unknown> {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: crumbs.map((crumb, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: crumb.name,
      item: abs(crumb.path),
    })),
  };
}

export interface TripInput {
  name: string;
  description?: string | null;
  inLanguage: string;
  touristType: string;
  path: string;
  imagePath?: string | null;
  /** Catalogue adult price; omitted for on-request tours. */
  price?: { amount: string; currencyCode: string } | null;
}

/** TouristTrip: catalogue facts only. No ratings, reviews or availability. */
export function touristTrip(input: TripInput): Record<string, unknown> {
  return {
    "@context": "https://schema.org",
    "@type": "TouristTrip",
    name: input.name,
    description: input.description ?? undefined,
    url: abs(input.path),
    inLanguage: input.inLanguage,
    touristType: input.touristType,
    image: input.imagePath ? abs(input.imagePath) : undefined,
    provider: { "@type": "TravelAgency", "@id": `${siteWebsite}/#organization`, name: brandName, url: siteWebsite },
    ...(input.price
      ? { offers: { "@type": "Offer", price: input.price.amount, priceCurrency: input.price.currencyCode, url: abs(input.path) } }
      : {}),
  };
}

/** First/last sentence helpers for building descriptions from existing copy. */
export function sentences(text: string): string[] {
  return text.split(/(?<=[.!?؟])\s+/).filter(Boolean);
}
