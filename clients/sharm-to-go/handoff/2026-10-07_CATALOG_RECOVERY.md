# Catalog recovery — 2026-10-07

Status: locally implemented and verified, uncommitted. This is the bounded
STG-CATALOG-RECOVERY slice of WEGO-010-A, authorized by the owner's request to
complete catalog gaps. No production/shared catalog writes or publication.

## Problem and resulting behavior

The original tool recorded service IDs only after approval. If creation
committed but review/approval or its response failed, a retry could create a
second service. A successful clean rerun did not prove interruption safety.

The current tool validates the entire manifest before API calls, saves a
create intent before POST, then saves the returned ID and every lifecycle
step atomically. On resume it reads the actual saved service: DRAFT proceeds
to review, REVIEW to approval, APPROVED skips. It verifies content/category;
changed, missing, published or archived records require reconciliation.

State format 2 binds the file to its API URL and each entry to a digest of its
actual catalog fields. A different target or changed manifest cannot cause
false skips. Old flat state is migrated only after verifying all known IDs
against the actual target. One state-file lock prevents concurrent use of
that file. HTTP calls have bounded connect/read timeouts (5/30 seconds);
failures return a nonzero exit code. Credentials stay in the environment;
response bodies and tokens are not logged.

The backend service-create API has no idempotency key. If its response is
lost, a CREATE_PENDING entry with no known ID blocks automatic retry.
Inspect Services in the ERP, then reconcile explicitly:

```bash
# STG_API_BASE, STG_ADMIN_EMAIL, STG_ADMIN_PASSWORD must already be set.
python3 clients/sharm-to-go/scripts/import_catalog.py \
  --state-file /your/target-specific/state.json \
  --recover-service 'INTERNAL_REF=existing-service-UUID'
```

The recovery GET verifies the selected service against the manifest before
linking it. Only when you have confirmed that the earlier create made no
service, use `--retry-uncreated INTERNAL_REF` with the same state file.
Do not delete the file to bypass an unresolved outcome. Keep its backup,
use one state file/importer per target, and avoid editing catalog records
concurrently with import approval. Losing the file or using a new one is
not protected by server-side deduplication.

`--dry-run` needs no credentials and makes no API calls. It validates/lists
the manifest; it does not assert remote status or apply recovery flags.
Import still stops at APPROVED and never publishes. A placeholder media
record still does not enforce a backend real-photo/approved-rights gate.

## Executable evidence

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s clients/sharm-to-go/scripts -p 'test_*.py'
python3 clients/sharm-to-go/scripts/import_catalog.py --dry-run
```

14/14 tests passed, using a real local HTTP server for response loss. They
cover lost create/review/approve responses, explicit recovery, a confirmed
uncreated retry, clean repetition, target mismatch, changed local/remote
content, wrong legacy IDs, archived categories, invalid input, state locking
and a failed atomic file replacement preserving the previous checkpoint.
The full manifest validates: seven categories, 41 unique service references,
83 options, required EN/AR content, prices and participant counts. No prices,
policies or catalog facts were changed by this slice.

Against the actual isolated PostgreSQL 18.4/backend fixture, a temporary HTTP
proxy forwarded the real create and deliberately dropped its successful
reply. A blind repeat was refused; explicit recovery then approved the
existing service, and another repeat skipped it. Exactly one service existed.
Dropping a real approve reply also recovered via GET with exactly one service.
Only synthetic local records were used; neither was published. Evidence:
`/tmp/stg-catalog-live-interruption.py`,
`/tmp/stg-catalog-live-interruption.log`.

A copied legacy state for the actual 41-service import migrated to format 2:
`created=0 resumed=0 skipped(already approved)=41 failed=0`. Thus the tool
remains compatible with the current real staff API and earlier state files.
Logs: `/tmp/stg-catalog-v2-live.log`, `/tmp/stg-catalog-recovery-tests.log`,
`/tmp/stg-catalog-dry-run.log`. These focused Python checks supplement the
previous successful official Sharm quality gate; backend/UI code did not
change after that gate.
