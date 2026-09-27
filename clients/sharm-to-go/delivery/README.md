# Sharm To Go — complete delivery workbook

This folder is the operational handoff for every agent continuing Sharm To Go.
It turns the product strategy into ordered, checkable work across backend,
website, ERP and mobile.

## How an agent uses this workbook

1. Read `00_CURRENT_STATE.md`, then the active phase file.
2. Confirm the dependency gate at the top of that phase is satisfied.
3. Work on one coherent packet at a time; do not mark future work complete.
4. Change `[ ]` to `[x]` only after implementation **and** the stated evidence
   exist. A created file or passing compile alone is not completion.
5. Add the commit hash and evidence to `07_ACCEPTANCE_AND_EVIDENCE.md`.
6. Update `docs/execution/WEGO_EXECUTION_BOARD.md` without breaking its
   canonical-status invariant.
7. Run `bash scripts/sharm-to-go-check.sh` before handoff.

## Checkbox vocabulary

- `[ ]` — not started or not proven.
- `[x]` — implemented, verified and recorded.
- `BLOCKED:` — cannot proceed without a named owner/external input.
- `DEFERRED:` — intentionally outside the current phase; not a failure.

Never use `[x]` for “code written but tests not run,” “expected to work,” or
“waiting for someone else to verify.”

## Required order

| Order | File | Outcome |
|---:|---|---|
| 0 | `00_CURRENT_STATE.md` | Baseline and safety constraints understood |
| 1 | `01_REQUEST_AND_BOOKING.md` | A durable customer request exists |
| 2 | `02_OPERATIONS_ERP.md` | Staff can operate the request lifecycle |
| 3 | `03_MARKETING_WEBSITE.md` | Marketing pages convert into the real request |
| 4 | `04_MOBILE_APP.md` | Mobile uses the same catalog and request contract |
| 5 | `05_VERTICAL_EXPANSION.md` | Dining, cars, stays and offers expand safely |
| 6 | `06_LAUNCH_AND_OPERATIONS.md` | Production readiness and controlled launch |
| Every phase | `07_ACCEPTANCE_AND_EVIDENCE.md` | Evidence and owner inputs are traceable |

## Non-negotiable product rules

- Canonical brand: **Sharm To Go**.
- Canonical line: **Sharm To Go. Where you must go.**
- Public promise: a local travel companion that helps choose, request and
  confirm the right Sharm experience for the traveller and budget.
- Website, mobile and ERP use one backend contract and one request reference.
- A request or WhatsApp message is not a confirmed booking.
- No invented availability, rating, photo right, customer review or historical
  proof. Owner-approved prices/policies remain traceable.
- Sharm Divers Club remains a separate application, schema and product.
- The seven owner-supplied mockups communicate scope only; they are not assets.

## Definition of complete

A phase is complete only when all required boxes are checked, its automated and
live evidence is recorded, regressions are green, and no blocking TODO is hidden
in prose or code comments.
