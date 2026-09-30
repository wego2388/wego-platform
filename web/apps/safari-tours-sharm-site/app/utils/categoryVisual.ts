/** Visual identity per tour category: colour token and line icon (no emoji). */
export type CategoryKey = "SEA" | "DESERT" | "CULTURAL" | "SHOWS" | "TRANSFERS";

export const CATEGORY_VISUAL: Record<
  CategoryKey,
  { tone: "sea" | "desert" | "cultural" | "shows" | "transfers"; icon: string; colorVar: string }
> = {
  SEA: { tone: "sea", icon: "lucide:sailboat", colorVar: "--sts-cat-sea" },
  DESERT: { tone: "desert", icon: "lucide:sunset", colorVar: "--sts-cat-desert" },
  CULTURAL: { tone: "cultural", icon: "lucide:landmark", colorVar: "--sts-cat-cultural" },
  SHOWS: { tone: "shows", icon: "lucide:sparkles", colorVar: "--sts-cat-shows" },
  TRANSFERS: { tone: "transfers", icon: "lucide:bus", colorVar: "--sts-cat-transfers" },
};
