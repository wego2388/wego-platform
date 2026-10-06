# Catalog import pipeline — task #66, evidence record

**Status:** built and verified end-to-end against a real local stack. Not
yet run against any shared/staging/production environment — that run needs
its own explicit authorization per AGENTS.md ("do not deploy or touch
production unless explicitly authorized").

## What this delivers

- `clients/sharm-to-go/content-research/import-manifest.json` — all 41
  merged-catalog services (24 kept Safari trips + 17 kept legacy concepts),
  fully structured to the real `UpsertServiceRequest` shape: category,
  fulfilmentModel=DIRECT, confirmationType, cancellation policy (EN+AR),
  pickup info, inclusions/exclusions, one or more priced options, and
  placeholder media. Generated from the owner-approved source facts —
  `content-research/from-safari/catalog-facts.json` for the Safari trips,
  `SHARM_TO_GO_SERVICE_INTAKE_SHEETS.md` for the legacy concepts — plus the
  original EN/AR copy written in `original-content/*.md` (tasks #63–#65).
  No price, fact, or policy wording was invented: Safari prices use the
  owner's own locked EUR→EGP rate (59.80, 2026-09-20, rounded to EGP 50,
  no margin); legacy prices are copied verbatim from the intake sheets.
- `clients/sharm-to-go/scripts/import_catalog.py` — idempotent importer
  that logs into the real staff API, creates any missing categories, then
  for each service: `create` (DRAFT) → `submit-for-review` → `approve`.
  It never calls `publish` — every service still carries placeholder media
  (`rightsEvidence: "Pending real rights-cleared media from operator..."`),
  which is the one remaining real gap per the 2026-10-02 handoff, and
  `publish()` already refuses without real rights-cleared media. State is
  tracked in a local JSON file (path given via `--state-file`, not
  committed) so a re-run skips everything already created.

## Category taxonomy

Taken directly from the source documents' own category assignments
(Safari's `catalog-facts.json` category field; the legacy intake sheets'
own `Category:` line), not invented fresh — 7 categories, 41 services:

| Category | Code | Count |
|---|---|---|
| Desert Adventures | `desert` | 9 |
| Sea Adventures | `sea` | 11 |
| City & Culture | `culture` | 9 |
| Relaxation & Shows | `entertainment` | 1 |
| Transfers | `transfers` | 3 |
| Family Activities | `family` | 3 |
| Heritage & Day Trips | `heritage-day-trips` | 5 |

## Confirmation type

`INSTANT` for 34 services; `STAFF_REVIEW` for 7 — the same rule the owner
already applied to the legacy concepts (flight seats, cross-border ferry,
or contracted overnight hotel/camp the business can't verify itself),
applied consistently to the 2 Safari flight trips too:
`cairo-by-plane`, `luxor-by-plane`, `STG-TRN-002`, `STG-PT-007`,
`STG-DAY-004`, `STG-MUL-001`, `STG-MUL-002`.

## Verification performed (2026-10-06)

Ran against a fresh, throwaway local stack — real Postgres 16, the real
backend jar (rebuilt from current `main` of this worktree, including the
V8 `travel_request_notification` migration from the parallel STG-NOTIFY
session), real admin bootstrap via the interactive `bootstrap-admin`
profile (not a SQL shortcut):

1. `--dry-run` listed all 41 pending services correctly.
2. A real run created 7 categories and all 41 services, each taken through
   `submit-for-review` → `approve` via live HTTP calls. **0 failures.**
3. Re-running the same command skipped all 41 (idempotency confirmed).
4. `GET /public/services` returned 0 results — confirming publish was
   correctly never called and nothing leaks to the public catalog before
   real media exists.
5. Fetched one multi-option service (`STG-PT-002`, 8 priced options) back
   from the staff API and confirmed every field — name, description,
   cancellation policy, all 8 option prices/labels — round-tripped
   exactly as written in the manifest.

Test containers and the test backend process were stopped and removed
after verification; nothing was left running.

## What's next

Once the owner supplies real, rights-cleared photos per service, replace
each service's placeholder `media` entry (via `PUT /services/{id}`) and
call `publish`. That step is deliberately not automated here — it depends
on real assets that don't exist yet.
