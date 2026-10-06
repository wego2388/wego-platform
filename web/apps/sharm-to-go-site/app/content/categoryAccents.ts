// One accent per discovery category, cycled by position — mirrors
// clients/sharm-to-go/design/tokens.json's `categoryAccent` map (Sea,
// Desert, Transfers, City) so a real, dynamically-fetched category list
// (experiences/index.vue) gets the same distinct-per-category identity as
// the homepage's static four, instead of one flat color repeated.
//
// `text` and `solid` deliberately do NOT use the raw brand-sun/sky/
// terracotta Tailwind colors (`text-sharm-sun` etc.) — axe-core proved
// those fail WCAG AA color-contrast as plain text (sun 1.99:1 on white)
// and `solid` fails too once paired with white text (sky/terracotta
// 4.48:1 / 4.24:1, both under the 4.5:1 floor) — see main.css's own
// comment on --stg-color-{sun,sky,terracotta}-text/-solid for the
// verified replacement values. `onSolidText` exists because sun's fix is
// different in kind, not just degree: no text color pairs legibly with
// sun's own vivid, unchanged background — the design spec itself says so
// (tokens.json's note that sun must carry *dark* text, never light) — so
// sun pairs with dark ink instead of white on its solid chip.
export interface CategoryAccent {
  text: string;
  bg: string;
  ring: string;
  solid: string;
  onSolidText: string;
}

export const categoryAccents: CategoryAccent[] = [
  { text: "text-sharm-sea", bg: "bg-sharm-lagoon", ring: "border-sharm-sea-bright/25", solid: "bg-sharm-sea-bright", onSolidText: "text-white" },
  { text: "text-sharm-sun-text", bg: "bg-sharm-sand", ring: "border-sharm-sun/30", solid: "bg-sharm-sun", onSolidText: "text-sharm-on-sun" },
  { text: "text-sharm-sky-text", bg: "bg-sharm-sky/12", ring: "border-sharm-sky/25", solid: "bg-sharm-sky-solid", onSolidText: "text-white" },
  { text: "text-sharm-terracotta-text", bg: "bg-sharm-terracotta/12", ring: "border-sharm-terracotta/25", solid: "bg-sharm-terracotta-solid", onSolidText: "text-white" },
];

export function accentForIndex(index: number): CategoryAccent {
  const length = categoryAccents.length;
  const safeIndex = ((index % length) + length) % length;
  return categoryAccents[safeIndex]!;
}

// Same position-cycling as accentForIndex, for MockPhoto's gradient tone —
// kept as a parallel array (not folded into CategoryAccent) since a tone
// is a MockPhoto-specific concept, not a generic accent property every
// consumer of categoryAccents needs.
const categoryTones = ["sea", "desert", "transfers", "city"] as const;
export type CategoryTone = (typeof categoryTones)[number];

export function toneForIndex(index: number): CategoryTone {
  const length = categoryTones.length;
  const safeIndex = ((index % length) + length) % length;
  return categoryTones[safeIndex]!;
}
