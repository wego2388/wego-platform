# Sharm To Go — acceptance, owner inputs and evidence ledger

Agents append evidence; they do not erase prior entries. Keep secrets and real
customer PII out of this file.

## Packet evidence template

Copy this block for every completed packet:

```md
### YYYY-MM-DD — Packet name

- Commit: `<hash>`
- Scope completed:
  - [x] checklist item/path
- Automated evidence:
  - `<command>` — `<result/count>`
- Live evidence:
  - environment and synthetic journey performed
- Regression evidence:
  - Sharm Divers Club checks performed
- Review:
  - Tier and reviewer/result
- Residual risks:
  - explicit risk or `none beyond documented roadmap`
- Owner inputs consumed:
  - source/approval, never secret values
- Next unchecked gate:
  - file + checkbox
```

## Current proven baseline

### 2026-09-29 — new-account onboarding and pause alignment

- Commit: `uncommitted documentation checkpoint on aff1a27`
- Scope completed:
  - [x] Added one dated start point for new Codex and Claude accounts.
  - [x] Recorded exact worktree, branch, hashes and two local-only commits.
  - [x] Defined single-writer and independent-review account roles.
  - [x] Aligned this isolated worktree's board to the current paused WEGO-010
    state without changing product code or historical evidence.
  - [x] Corrected the active README's stale Packet 1E claim: `STG-TRN-001` was
    proven through `APPROVED`, but nothing was published to production.
- Automated evidence:
  - `git diff --check` — passed.
  - `bash scripts/repository-check.sh` — passed; board invariants valid with no
    active mission/packet in this paused worktree.
- Live evidence:
  - Git inspected from the isolated worktree; baseline HEAD `aff1a27`, remote
    Sharm branch `3a9a297`, and two local-only continuation commits confirmed.
- Regression evidence:
  - Documentation-only change; no backend, web or mobile executable file was
    changed. The product gate was not rerun and is not claimed.
- Review:
  - Tier 2 documentation coordination; self-verified.
- Residual risks:
  - The preserved branch is intentionally divergent from current `origin/main`.
    Reconciliation is a future owner-authorized resume gate, not part of this
    checkpoint.
- Owner inputs consumed:
  - The owner requested new-account onboarding and explicitly left product
    thinking/implementation to Codex and the new Claude account.
- Next unchecked gate:
  - Explicit owner reactivation of WEGO-010, then the resume/integration gate in
    `../handoff/2026-09-29_NEW-AGENTS_START-HERE.md`.

### 2026-09-27 — brand and conversion preparation

- Commit: `4be891e`
- [x] Customer-facing positioning changed from provider-marketplace language to
  the local travel-companion promise.
- [x] Brand/growth strategy and cross-surface conversion plan added.
- [x] About, FAQ, Contact, Privacy and Terms routes added.
- [x] Homepage proof/story, footer/navigation and WhatsApp action added.
- Automated evidence:
  - `pnpm --filter @wego/sharm-to-go-site test` — 27/27.
  - site typecheck and lint — clean.
  - production build — clean.
  - `bash scripts/repository-check.sh` and `git diff --check` — clean.
- Live evidence:
  - `/`, `/about`, `/faq`, `/contact`, `/privacy`, `/terms`, `/sitemap.xml`
    returned HTTP 200 from the production Nitro output on localhost.
  - English dark-mode homepage and About page inspected visually.
- Residual risk:
  - request/booking is still design/planning only; no public write or ERP queue.

## Owner input register

| Input | Status | Used by | Notes |
|---|---|---|---|
| Brand name `Sharm To Go` | Approved | All surfaces | Do not alternate legal/product name with `Charm To Go` |
| Line `Where you must go` | Approved | Marketing | Canonical English line |
| 25+ years / 3M+ customers / 5M+ trips / 24-7 support | Approved claim | Web/marketing | Owner retains substantiation before regulated/paid publication |
| WhatsApp `+20 10 0141 3469` | Supplied | Contact/WhatsApp | Centralized in `app/content/contact.ts` |
| Email `info@sharmtogo.com` | Supplied | Contact/email | Domain deployment still pending |
| Seven concept mockups | Scope reference only | Product planning | Never publish as assets or copy their sample facts |
| Production domain/server | BLOCKED: owner later | Launch | Do not guess |
| Rights-cleared real photos | BLOCKED: owner later | Publication | Required before real visual catalog launch |
| Payment merchant accounts | DEFERRED | Payment phase | Never commit credentials |

## Cross-surface release matrix

| Capability | Backend | Website | ERP | Mobile | Live proof |
|---|---|---|---|---|---|
| Published catalog | [x] | [x] | [x] | [x] bundled snapshot | [x] engineering/synthetic |
| Marketing/trust foundation | n/a | [x] | n/a | [ ] alignment pass | [x] web |
| Customer request | [ ] | [ ] | [ ] | [ ] | [ ] |
| Confirmation lifecycle | [ ] | [ ] status | [ ] actions | [ ] status | [ ] |
| Shareable summary | [ ] | [ ] | [ ] | [ ] | [ ] |
| Availability/capacity | [ ] | [ ] | [ ] | [ ] | [ ] |
| Payment/refund | [ ] | [ ] | [ ] | [ ] | [ ] |
| Dining | [ ] | [ ] | [ ] | [ ] | [ ] |
| Car rental | [ ] | [ ] | [ ] | [ ] | [ ] |
| Accommodation | [ ] | [ ] | [ ] | [ ] | [ ] |

No row is complete until its `Live proof` box is checked with recorded evidence.
