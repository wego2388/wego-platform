export const wegoDesignTokens = Object.freeze({
  color: Object.freeze({
    canvas: "#f4f7f6",
    surface: "#ffffff",
    ink: "#102a2e",
    muted: "#52686b",
    border: "#cfdddc",
    accent: "#087f74",
    accentSoft: "#d9f2ee",
    focus: "#ffb000",
    success: "#0f7a3d",
    successSoft: "#dcf5e4",
    warning: "#92600a",
    warningSoft: "#fbead0",
    danger: "#b3261e",
    dangerSoft: "#fbdcd9",
  }),
  radius: Object.freeze({
    card: "1.25rem",
    control: "0.75rem",
  }),
});

export type WegoDesignTokens = typeof wegoDesignTokens;
