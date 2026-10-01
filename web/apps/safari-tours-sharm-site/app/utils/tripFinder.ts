import type { TourCategory } from "@wego/api-contract";
import type { CatalogTour } from "../composables/useCatalog";
import { durationBucket, durationHours } from "./tourDuration";

/**
 * Rule-based "help me choose": hard filters for what the visitor ruled out,
 * a small score for what fits best, and a reason for every suggestion. No
 * hidden ranking, no invented facts — only catalogue data.
 */
export const FINDER_LIKES = ["DESERT", "SEA", "CULTURAL", "SHOWS"] as const;
export const FINDER_WHO = ["family", "couple", "friends", "solo"] as const;
export const FINDER_TIME = ["short", "half", "full", "any"] as const;
export const FINDER_BUDGET = ["25", "50", "100", "any"] as const;

export interface FinderAnswers {
  like: (typeof FINDER_LIKES)[number] | null;
  who: (typeof FINDER_WHO)[number] | null;
  time: (typeof FINDER_TIME)[number];
  budget: (typeof FINDER_BUDGET)[number];
}

export type FinderReason = "category" | "duration" | "budget" | "family" | "group";

export interface FinderSuggestion {
  entry: CatalogTour;
  reasons: FinderReason[];
}

function pick<T extends readonly string[]>(values: T, value: unknown): T[number] | null {
  return typeof value === "string" && (values as readonly string[]).includes(value) ? (value as T[number]) : null;
}

export function answersFromQuery(query: Record<string, unknown>): FinderAnswers {
  return {
    like: pick(FINDER_LIKES, typeof query.like === "string" ? query.like.toUpperCase() : null),
    who: pick(FINDER_WHO, query.who),
    time: pick(FINDER_TIME, query.time) ?? "any",
    budget: pick(FINDER_BUDGET, query.budget) ?? "any",
  };
}

export function answersToQuery(answers: FinderAnswers): Record<string, string> {
  const query: Record<string, string> = {};
  if (answers.like) query.like = answers.like.toLowerCase();
  if (answers.who) query.who = answers.who;
  if (answers.time !== "any") query.time = answers.time;
  if (answers.budget !== "any") query.budget = answers.budget;
  return query;
}

/** Price one guest pays: per person, or a unit's price shared by its seats. */
export function pricePerGuest(entry: CatalogTour): number {
  const tour = entry.tour;
  if (tour.priceBasis === "PER_UNIT") {
    const perGuest = tour.priceOptions.filter((o) => o.seatsPerUnit > 0).map((o) => Number(o.price.amount) / o.seatsPerUnit);
    // A per-unit tour without usable options has no price a budget can be compared with.
    return perGuest.length ? Math.min(...perGuest) : Number.POSITIVE_INFINITY;
  }
  return Number(tour.priceAdult.amount);
}

function fitsTime(entry: CatalogTour, time: FinderAnswers["time"]): boolean {
  if (time === "any") return true;
  const bucket = durationBucket(entry.tour.durationText);
  if (bucket === null) return false;
  if (time === "short") return bucket === "short";
  if (time === "half") return bucket === "short" || bucket === "half";
  return bucket !== "multi";
}

export function suggestTours(catalog: CatalogTour[], answers: FinderAnswers, limit = 6): FinderSuggestion[] {
  const scored: { suggestion: FinderSuggestion; score: number; order: number }[] = [];
  for (const entry of catalog) {
    const tour = entry.tour;
    if (tour.tourType !== "TOUR" || (tour.category as TourCategory) === "TRANSFERS") continue;
    if (answers.like && tour.category !== answers.like) continue;
    if (!fitsTime(entry, answers.time)) continue;
    if (answers.budget !== "any" && pricePerGuest(entry) > Number(answers.budget)) continue;
    const hours = durationHours(tour.durationText);
    if (answers.who === "family" && (hours === null || hours > 12)) continue;

    const reasons: FinderReason[] = [];
    let score = 0;
    if (answers.like) {
      reasons.push("category");
      score += 3;
    }
    if (answers.time !== "any") {
      reasons.push("duration");
      score += durationBucket(tour.durationText) === answers.time ? 2 : 1;
    }
    if (answers.budget !== "any") {
      reasons.push("budget");
      score += 1;
    }
    if (answers.who === "family" && hours !== null && hours <= 6) {
      reasons.push("family");
      score += 1;
    }
    if (answers.who === "friends" && tour.priceBasis === "PER_UNIT") {
      reasons.push("group");
      score += 1;
    }
    scored.push({ suggestion: { entry, reasons }, score, order: tour.sortOrder });
  }
  return scored
    .sort((a, b) => b.score - a.score || a.order - b.order)
    .slice(0, limit)
    .map((s) => s.suggestion);
}
