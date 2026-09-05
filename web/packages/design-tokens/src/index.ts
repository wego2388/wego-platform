const lightColor = Object.freeze({
  canvas: "#f4f7f6",
  surface: "#ffffff",
  surfaceRaised: "#ffffff",
  surfaceSunken: "#eef3f2",
  surfaceHover: "#eef3f2",
  surfacePressed: "#e3ebe9",
  surfaceOverlay: "rgb(16 42 46 / 50%)",
  surfaceInverse: "#102a2e",
  ink: "#102a2e",
  inkInverse: "#eaf3f1",
  muted: "#52686b",
  border: "#cfdddc",
  borderStrong: "#63817e",
  accent: "#087f74",
  accentSoft: "#eefaf8",
  focus: "#a35f00",
  success: "#0f7a3d",
  successSoft: "#dcf5e4",
  warning: "#92600a",
  warningSoft: "#fbead0",
  danger: "#b3261e",
  dangerSoft: "#fbdcd9",
  info: "#1d5fb4",
  infoSoft: "#dbe9fb",
});

// See tokens.css's own `:root[data-theme="dark"]` block for why this is
// opt-in per consuming app rather than a `prefers-color-scheme` default —
// this object mirrors that block exactly and both are checked for drift
// by design-tokens.test.ts.
const darkColor = Object.freeze({
  canvas: "#0b1817",
  surface: "#112120",
  surfaceRaised: "#16302e",
  surfaceSunken: "#0d1c1b",
  surfaceHover: "#1a3230",
  surfacePressed: "#1f3a37",
  surfaceOverlay: "rgb(5 12 12 / 60%)",
  surfaceInverse: "#eaf3f1",
  ink: "#eaf3f1",
  inkInverse: "#102a2e",
  muted: "#9db3b0",
  border: "#24403d",
  borderStrong: "#4d7975",
  accent: "#35d6bd",
  accentSoft: "#123634",
  focus: "#ffc247",
  success: "#4ade80",
  successSoft: "#113220",
  warning: "#f5b942",
  warningSoft: "#3a2a0d",
  danger: "#ff6b61",
  dangerSoft: "#3a1512",
  info: "#7db2f2",
  infoSoft: "#132a44",
});

export const wegoDesignTokens = Object.freeze({
  color: Object.freeze({
    light: lightColor,
    dark: darkColor,
  }),
  radius: Object.freeze({
    card: "1.25rem",
    control: "0.75rem",
    pill: "999px",
  }),
  zIndex: Object.freeze({
    dropdown: 1000,
    sticky: 1100,
    overlay: 1200,
    modal: 1300,
    toast: 1400,
  }),
  motion: Object.freeze({
    durationFast: "120ms",
    durationStandard: "200ms",
    durationSlow: "320ms",
    easingStandard: "cubic-bezier(0.2, 0, 0, 1)",
    easingDecelerate: "cubic-bezier(0, 0, 0, 1)",
    easingAccelerate: "cubic-bezier(0.3, 0, 1, 1)",
  }),
  size: Object.freeze({
    controlMin: "2.75rem",
  }),
});

export type WegoDesignTokens = typeof wegoDesignTokens;
