export type ConnectionPreference = { saveData?: boolean; effectiveType?: string };

/** A poster is the default on phones, reduced motion and constrained networks. */
export function allowHeroVideo(desktop: boolean, reducedMotion: boolean, connection?: ConnectionPreference): boolean {
  return desktop && !reducedMotion && !connection?.saveData && !["slow-2g", "2g", "3g"].includes(connection?.effectiveType ?? "");
}
