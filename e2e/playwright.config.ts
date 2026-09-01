import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: 0,
  // Default 5000ms `expect()` timeout proved marginal on GitHub's shared
  // ubuntu-24.04 CI runner specifically — two real CI runs each hit one
  // unrelated, ordinary post-action UI-update assertion (an offering title
  // appearing, a post-login banner appearing) timing out by itself, on
  // steps that had already succeeded identically earlier in the same run.
  // Never reproduced locally or in Codex's own review sandbox. Doubled
  // here rather than adding retries, which would risk masking a real
  // deterministic failure instead of this demonstrated timing margin.
  expect: { timeout: 10_000 },
  reporter: [["list"]],
  use: {
    baseURL: process.env.WEGO_E2E_BASE_URL ?? "http://127.0.0.1:58080",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
