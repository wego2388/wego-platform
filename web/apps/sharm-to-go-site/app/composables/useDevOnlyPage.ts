// Internal design/booking-flow preview pages (design-system.vue,
// booking-preview.vue) have no auth of their own and were never meant for a
// real visitor or a search engine to land on — booking-preview.vue in
// particular simulates a payment flow that isn't connected to anything
// real, which would actively mislead a customer who found it.
// `import.meta.env.PROD` is a Vite build-time constant, true only for a real
// `nuxt build` production bundle — false under `nuxt dev` and under Vitest
// (MODE defaults to "test" there, so PROD is false), so this stays usable in
// local dev and in the test suite while being dead-code-eliminated out of
// the production bundle entirely, not a runtime check that could be
// bypassed.
export function useDevOnlyPage(): void {
  if (import.meta.env.PROD) {
    throw createError({ statusCode: 404, statusMessage: "Page not found", fatal: true });
  }
}
