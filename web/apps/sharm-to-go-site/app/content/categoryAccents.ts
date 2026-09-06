// One accent per discovery category, cycled by position — mirrors
// clients/sharm-to-go/design/tokens.json's `categoryAccent` map (Sea,
// Desert, Transfers, City) so a real, dynamically-fetched category list
// (experiences/index.vue) gets the same distinct-per-category identity as
// the homepage's static four, instead of one flat color repeated.
export interface CategoryAccent {
  text: string;
  bg: string;
  ring: string;
  solid: string;
}

export const categoryAccents: CategoryAccent[] = [
  { text: "text-sharm-sea-bright", bg: "bg-sharm-lagoon", ring: "border-sharm-sea-bright/25", solid: "bg-sharm-sea-bright" },
  { text: "text-sharm-sun", bg: "bg-sharm-sand", ring: "border-sharm-sun/30", solid: "bg-sharm-sun" },
  { text: "text-sharm-sky", bg: "bg-sharm-sky/12", ring: "border-sharm-sky/25", solid: "bg-sharm-sky" },
  { text: "text-sharm-terracotta", bg: "bg-sharm-terracotta/12", ring: "border-sharm-terracotta/25", solid: "bg-sharm-terracotta" },
];

export function accentForIndex(index: number): CategoryAccent {
  const length = categoryAccents.length;
  const safeIndex = ((index % length) + length) % length;
  return categoryAccents[safeIndex]!;
}
