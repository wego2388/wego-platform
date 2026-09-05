# WEGO-015 Phase 1 — mobile customer app UX and regression contract

The frozen baseline for the mobile customer app half of WEGO-015. Built
by direct inspection of `src/commonMain/kotlin/com/wego/mobile/customer/`
and both real test files in `src/jvmTest/`. Testing here is Compose UI
testing (`runComposeUiTest`, JVM target, shared `commonMain` code) —
`onNodeWithText` is this codebase's equivalent of the website's
`wrapper.text()`/Playwright's `getByText`.

## Screen inventory (9 screens)

| Screen | File | Purpose |
|---|---|---|
| Home | `HomeScreen.kt` | Hero, real accreditation facts, entry points |
| Discover | `DiscoverScreen.kt` | Category-filterable offering list |
| Offering detail | `OfferingDetailScreen.kt` | Real price, WhatsApp inquiry |
| Dive Sites | `DiveSitesScreen.kt` | Real named site list |
| Dive site detail | `DiveSiteDetailScreen.kt` | Real linked trips, site-named WhatsApp inquiry |
| Package builder | `PackageBuilderScreen.kt` | Add/remove real offerings, running total |
| About | `AboutScreen.kt` | Company facts |
| Contact | `ContactScreen.kt` | Real contact channels |
| FAQ | `FaqScreen.kt` | Question groups |

Navigation and locale state live in `AppDestination.kt`/`AppLocaleState.kt`;
`WegoCustomerRoot.kt` is the app shell every test mounts via `setContent`.
Design tokens live in `SdcTokens.kt` (`SdcColor`/`SdcSpace`/`SdcRadius`/
`SdcType`), explicitly documented as "ported verbatim" from
`clients/sharm-divers-club/design/tokens.json` — see WEGO-015's board
entry for the real gap in verifying that stays true.

## The one rule every test enforces: never a fabricated fact (same as the website)

Real offering names and prices (`Intro Dive — 30 minutes` at `€50`,
`Ras Mohammed beginner dive — 30 minutes` at `€60`), real site names
(Ras Mohammed, Tiran, SS Thistlegorm), real offering codes (`SD02`,
`PC04`), the real WhatsApp handoff (`SiteCopy.WHATSAPP_URL`). A redesign
may restructure presentation; it must never alter what is actually said
— identical discipline to the website, verified independently here.

## Frozen text/interaction selectors

### Locale toggle

```kotlin
onNodeWithText("Sharm El Sheikh · PADI 5 Star Dive Center").assertExists()
onNodeWithText("AR").performClick()
onNodeWithText("شرم الشيخ · مركز PADI 5 نجوم").assertExists()
```

The exact English and Arabic hero strings, and the `"AR"` toggle label
itself, are hard test dependencies.

### Navigation labels

`"Discover"`, `"Dive Sites"`, `"Package"` are the exact tab/nav labels
`onNodeWithText(...).performClick()` drives navigation through in both
test files. Renaming any of these is a breaking change to update in the
same commit as the rename, not after.

### WhatsApp button labels (three distinct ones, not interchangeable)

- `"Send an inquiry on WhatsApp"` — offering detail and dive site detail
- `"Message us on WhatsApp"` — home screen
- `"Send this package on WhatsApp"` — package builder

Each is asserted with a distinct expected URL shape: the offering/site
ones assert the destination or site name appears in the opened URL
(`assertTrue(url.contains("Tiran"))`); the package one asserts the real
offering code and percent-encoded price appear
(`url.contains("SD02")`, `url.contains("%E2%82%AC50")`).

### High-risk pattern: a positional selector across identically-labeled buttons

```kotlin
onAllNodesWithText("Add")[0].performClick()
```

In `DiveSitesAndPackageBuilderTest.kt`, both package-builder tests select
the **first** "Add" button by list position, relying on
`DiveCatalog.offerings[0]` being `SD02` ("Intro Dive — 30 minutes", €50).
This is the exact same fragility class WEGO-014 found and fixed in the
ERP's Reports page (three identically-labeled "Run" buttons selected by
DOM position) — the tests do defensively assert the real code/price
afterward, so a silent wrong-offering bug would fail loudly, but any
phase that reorders, re-categorizes, or filters the package-builder's
offering list must either confirm `offerings[0]` is still `SD02`, or
replace the positional selector with a named one (e.g. a per-row test
tag) in the same change — do not leave it dangling against a list whose
order has changed.

### `onAllNodesWithText("€50").assertCountEquals(2)`

The package builder's running-total test asserts the price string
appears **exactly twice** (once in the catalog row, once in the total) —
a real structural assertion about how many places on screen repeat a
given price, not just that the price appears somewhere.

## What Phase 1 does not include

No visual changes in this phase. This document is the baseline every
later phase in WEGO-015's mobile track is checked against, alongside the
website's own `UX_REDESIGN_CONTRACT.md`.
