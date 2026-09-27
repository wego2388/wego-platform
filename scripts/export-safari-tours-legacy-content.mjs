#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const sourceBaseUrl = "https://safaritourssharm.com";
const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const outputPath = resolve(
  repositoryRoot,
  "clients/safari-tours-sharm/content-research/legacy-wordpress-export.json",
);

async function fetchText(url) {
  const response = await fetch(url, {
    headers: { "User-Agent": "Wego Safari Tours legacy content exporter/1.0" },
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) {
    throw new Error(`GET ${url} failed with HTTP ${response.status}`);
  }
  return response.text();
}

async function fetchJson(url) {
  return JSON.parse(await fetchText(url));
}

function decodeHtml(value) {
  const named = new Map([
    ["amp", "&"],
    ["apos", "'"],
    ["gt", ">"],
    ["lt", "<"],
    ["nbsp", " "],
    ["quot", '"'],
  ]);

  return value.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (match, entity) => {
    if (entity.startsWith("#x")) {
      return String.fromCodePoint(Number.parseInt(entity.slice(2), 16));
    }
    if (entity.startsWith("#")) {
      return String.fromCodePoint(Number.parseInt(entity.slice(1), 10));
    }
    return named.get(entity.toLowerCase()) ?? match;
  });
}

function htmlToText(html) {
  return decodeHtml(
    html
      .replace(/<!--.*?-->/gs, " ")
      .replace(/<(script|style|noscript|svg)\b[^>]*>.*?<\/\1>/gis, " ")
      .replace(/<(br|\/p|\/div|\/li|\/h[1-6]|\/section|\/article)>/gi, "\n")
      .replace(/<li\b[^>]*>/gi, "• ")
      .replace(/<[^>]+>/g, " "),
  )
    .split("\n")
    .map((line) => line.replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .join("\n");
}

function extractTourText(renderedHtml, title) {
  const lines = htmlToText(renderedHtml).split("\n");
  const normalizedTitle = decodeHtml(title).trim().toLowerCase();
  const start = lines.findIndex((line) => line.toLowerCase() === normalizedTitle);
  if (start < 0) return htmlToText(renderedHtml);

  const backLink = lines.findIndex((line, index) => index > start && line === "← Back to Tours");
  const bookLink = lines.findIndex((line, index) => index > start && line === "Book on WhatsApp");
  const end = backLink >= 0 ? backLink : bookLink >= 0 ? bookLink : lines.length - 1;
  return lines.slice(start, end + 1).join("\n");
}

function extractBookingChoices(pageHtml) {
  return [...pageHtml.matchAll(/<option\b[^>]*>(.*?)<\/option>/gis)]
    .map((match) => htmlToText(match[1]))
    .filter(Boolean);
}

async function fetchAllMedia() {
  const media = [];
  for (let page = 1; ; page += 1) {
    const batch = await fetchJson(
      `${sourceBaseUrl}/wp-json/wp/v2/media?per_page=100&page=${page}`
      + "&_fields=id,date,modified,slug,source_url,alt_text,title,caption,description,media_type,mime_type,media_details",
    ).catch((error) => {
      if (String(error).includes("HTTP 400")) return [];
      throw error;
    });
    if (batch.length === 0) break;
    media.push(...batch);
    if (batch.length < 100) break;
  }
  return media.sort((left, right) => left.id - right.id);
}

const [pages, tourRecords, media] = await Promise.all([
  fetchJson(
    `${sourceBaseUrl}/wp-json/wp/v2/pages?per_page=100`
    + "&_fields=id,date,modified,slug,status,link,title,content,excerpt,featured_media",
  ),
  fetchJson(
    `${sourceBaseUrl}/wp-json/wp/v2/tour?per_page=100`
    + "&_fields=id,date,modified,slug,status,link,title,content,excerpt,featured_media",
  ),
  fetchAllMedia(),
]);

const tours = await Promise.all(
  tourRecords
    .sort((left, right) => left.id - right.id)
    .map(async (tour) => {
      const renderedHtml = await fetchText(tour.link);
      return {
        id: tour.id,
        slug: tour.slug,
        title: decodeHtml(tour.title.rendered),
        status: tour.status,
        contentApprovalStatus: "UNVERIFIED_SOURCE",
        sourceUrl: tour.link,
        sourceCreatedAt: tour.date,
        sourceModifiedAt: tour.modified,
        featuredMediaId: tour.featured_media,
        wordpressContentHtml: tour.content.rendered,
        wordpressContentText: htmlToText(tour.content.rendered),
        renderedPageText: extractTourText(renderedHtml, tour.title.rendered),
      };
    }),
);

const normalizedPages = pages
  .sort((left, right) => left.id - right.id)
  .map((page) => ({
    id: page.id,
    slug: page.slug,
    title: decodeHtml(page.title.rendered),
    status: page.status,
    contentApprovalStatus: "UNVERIFIED_SOURCE",
    sourceUrl: page.link,
    sourceCreatedAt: page.date,
    sourceModifiedAt: page.modified,
    featuredMediaId: page.featured_media,
    contentHtml: page.content.rendered,
    contentText: htmlToText(page.content.rendered),
    excerptText: htmlToText(page.excerpt.rendered),
  }));

const bookingPage = pages.find((page) => page.slug === "booking")
  ?? pages.find((page) => page.slug === "contact");

const payload = {
  schemaVersion: 1,
  source: {
    type: "LEGACY_WORDPRESS_PUBLIC_SITE",
    baseUrl: sourceBaseUrl,
    wordpressApi: `${sourceBaseUrl}/wp-json/wp/v2`,
    sitemap: `${sourceBaseUrl}/wp-sitemap.xml`,
    retrievedAt: new Date().toISOString(),
    publicationRule: "RESEARCH_ONLY_UNTIL_OWNER_VERIFIED",
  },
  observedBusinessIdentity: {
    displayName: "Safari Tours Sharm",
    location: "Sharm El Sheikh, Egypt",
    phone: "+2 01111 292 690",
    email: "safaritourssharm@gmail.com",
    whatsapp: "https://wa.me/201111292690",
    heroPromise: "Explore the Red Sea & Sinai Desert in Style",
  },
  observedCategories: [
    "Safari & Desert Adventures",
    "Sea & Water Activities",
    "Shows & Relax Trips",
    "Cultural & Historical Tours",
    "Booking Room & Rent Car",
  ],
  bookingChoices: bookingPage ? extractBookingChoices(bookingPage.content.rendered) : [],
  pages: normalizedPages,
  tours,
  media: media.map((record) => ({
    ...record,
    rightsStatus: "UNVERIFIED",
    migrationStatus: "NOT_SELECTED",
  })),
};

const digestInput = JSON.stringify(payload);
const snapshot = {
  ...payload,
  snapshotSha256: createHash("sha256").update(digestInput).digest("hex"),
};

await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, `${JSON.stringify(snapshot, null, 2)}\n`, "utf8");

console.log(`Exported ${snapshot.pages.length} pages, ${snapshot.tours.length} tours, and ${snapshot.media.length} media records.`);
console.log(`Snapshot: ${outputPath}`);
console.log(`SHA-256: ${snapshot.snapshotSha256}`);
