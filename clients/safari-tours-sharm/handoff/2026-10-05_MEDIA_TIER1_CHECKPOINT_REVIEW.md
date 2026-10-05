# WEGO-016-MEDIA — independent Tier 1 checkpoint review

Date: 2026-10-05. Reviewer: `/root/media_tier1_review`, independent of the
application implementation agents. Workspace: `/home/wego/wego-safari-hardening`,
branch `wego-016-safari-hardening`, baseline HEAD
`894831594bfad5c987d1e904f1cce77ac0aff5b3`. This review evaluates the dirty,
uncommitted MEDIA source and its tested artifacts, not that baseline commit alone.

## Verdict and scope

**Limited checkpoint: no open reproduced code blocker in the reviewed MEDIA
scope. WEGO-016-MEDIA remains ACTIVE and NOT READY for whole-packet acceptance
or production release.** The outstanding acceptance and release gates below are
not waived by the passing tests.

The reviewer independently read AGENTS, the Engineering Constitution, current
execution-board MEDIA scope, review/collaboration governance and the Arabic
catalog/media implementation specification. Review covered V29 registry and
permissions, five-category seeding, upload replay and concurrency, domain/ports,
transaction/storage failure handling, managed-link guards, repositories,
controllers, bean wiring, public metadata boundaries, artifact isolation,
OpenAPI, image/storage hardening and the MEDIA editor/preview integration.
Existing SEC/ENQUIRY/payment changes were preserved and were not accepted again
under this review.

## Findings closed during review

1. **Blocking — committed files deleted after lost commit acknowledgement.**
   The original cleanup was independently reproduced with durable registry and
   category linkage but missing bytes. `MediaUploadService.kt:234–245` now
   reconciles generated asset IDs outside the closed transaction and deletes
   only when every ID is proven absent. Direct current-service probes retained
   bytes after commit acknowledgement failure and registry read failure, cleaned
   confirmed rollback and partial writes, and did not report uncertain success.
   Real PostgreSQL failure-wrapper regressions also pass.

2. **Blocking — small private images requested an absent w768 derivative.**
   An actual processor probe on a 400px JPEG produced only w360. The previous
   fixed w768 preview therefore returned 404. `StaffPhotoPreview.vue:23–24` now
   requests base when width is at most 768. Focused component tests and the final
   real browser suite decode genuine 600px tour/category replacement previews.

3. **Blocking — real multipart limits produced 401, then a socket reset.**
   The first real browser run exposed authenticated 10 MiB + 1 byte uploads
   returning 401 rather than 413; MockMultipartFile tests did not exercise that
   parser boundary. Installed Spring 7.0.8 bytecode independently confirmed that
   multipart parsing precedes controller selection.
   `MediaTransportExceptionHandler.kt:13–20` handles only
   `MaxUploadSizeExceededException`, without opening `/error` or weakening auth.
   Isolated Safari `application.yml:34–41` bounds Tomcat body discard at 12 MiB;
   file/request caps remain 10/11 MiB. Independent RANDOM_PORT HTTP tests now
   return structured 413/no-store for both file and total-request limits, with
   zero registry/link/media-file delta. The fresh edge/browser case also passes.

4. **Durability advisory — final directory entries were not fsynced.**
   Current `FilesystemAssetStorage` forces relevant directories after publication,
   temporary cleanup and deletion. Current-source storage tests pass. The actual
   pinned Alpine JRE/UID10001/read-only diagnostic passes required file operations
   and fsync calls. That tmpfs diagnostic alone does not prove persistence or
   power-loss survival; normal named-volume recreation is separately evidenced
   below. No power-loss durability claim is made.

5. **Optimizer classification advisory — encoded query/fragment sources escaped
   classification.** Direct execution reproduced this for managed derivatives and
   absolute source URLs. `managedMedia.ts` now strips decoded query/fragment text
   before classification; direct and unit regressions pass. This was **not a
   demonstrated data disclosure** under the private-volume/empty-IPX-domain
   configuration. Real relative and absolute encoded optimizer requests return
   404 without bytes. Nitro's final 404 error response emits `cache-control:
   no-cache`, overriding the guard's no-store header; this observation is retained
   explicitly and is not represented as a successful no-store optimizer response.

## Independently executed checks

The following Gradle commands were run from the workspace root with exclusive
host-build slots agreed with the implementation agent. `--rerun` forced the test
task to execute; these were not cached test-result claims.

```sh
JAVA_HOME=/home/wego/.jdks/temurin-25.0.3+9 PATH=/home/wego/.jdks/temurin-25.0.3+9/bin:$PATH ./gradlew :platform:apps:safari-tours-sharm:test --rerun --tests '*ToursOperatorMediaHttpTest' --tests '*MediaImageProcessorTest' --tests '*FilesystemAssetStorageTest' --tests '*CategoryMediaTest' --tests '*ProductIsolationIntegrationTest'
JAVA_HOME=/home/wego/.jdks/temurin-25.0.3+9 PATH=/home/wego/.jdks/temurin-25.0.3+9/bin:$PATH ./gradlew :platform:apps:safari-tours-sharm:test --rerun --tests '*ToursOperatorMediaHttpTest'
```

- Initial narrow run: 5 suites, 66 tests, zero failures/errors/skips, 29 seconds.
  HTTP14, processor24, filesystem17, category10, isolation1.
  Log: `/tmp/safari-media-independent-review.log`.
- Final transport-source rerun: 15 MEDIA HTTP tests, real RANDOM_PORT server and
  PostgreSQL, zero failures/errors/skips, 23 seconds. This repeats the earlier
  14 HTTP cases and adds the transport regression; do not add the two counts as
  distinct tests. Log: `/tmp/safari-media-independent-transport-review.log`.

Focused web commands were also independently executed in their respective app
directories:

```sh
# web/apps/safari-tours-sharm-erp
pnpm exec vitest run test/staffPhotoPreview.spec.ts test/tourMediaApi.spec.ts test/tourMediaEditor.spec.ts test/tourContentEditor.spec.ts test/tourContentPage.spec.ts test/tourContentApi.spec.ts
# web/apps/safari-tours-sharm-site
pnpm exec vitest run test/managedMedia.spec.ts
# foundry
pnpm run validate:manifests && pnpm run validate:openapi
```

Results: ERP 43/43; optimizer 3/3; release manifests, deterministic plans/locks,
negative dependency graphs and both OpenAPI descriptions valid.

Additional independent executable probes used actual processor/service classes,
controlled failing ports and a disposable PostgreSQL 18.4 container. Applying
actual V1/V2/Safari V3/V29 proved exactly five DRAFT categories, the permission
grant, owner/request uniqueness, rejection of an assetless approval and sixth
category, and protection of an asset referenced by a category. The exact owned
schema container was stopped and automatically removed; no other container or
volume was changed by the reviewer.

## Final artifacts and runtime evidence inspected independently

| Evidence | Verified result |
|---|---|
| Final Safari full log and JUnit XML | 27 suites / 289 tests; zero failures, errors or skips; `BUILD SUCCESSFUL` in 1m41s |
| Whole quality log | Generic 327 tests; web 730 tests and six completed builds; contracts, repository/governance checks pass |
| Fresh real MEDIA browser JSON | 12/12; one worker, one passing attempt per case; zero retries, skips, flakes or errors; 93.8s |
| Separate existing ERP UI-fixture regression JSON | 122/122; two workers; zero skips, flakes or errors; not a new backend/payment acceptance |
| Final running backend | Current JAR hash matches host build; UID10001:10001; read-only root; dedicated named media volume |
| Recreated backend/edge | Root-owned recreation succeeded; private 19,517-byte JPEG unchanged against registry SHA; same named volume retained |
| Reviewer public-image comparison after recreation | Same approved DESERT JPEG: 200, image/jpeg, 9,423 bytes, unchanged SHA and `no-store, private` |

Evidence paths:

- `/tmp/safari-media-backend-final-full.log`
- `/tmp/safari-media-quality-gate.log`
- `/tmp/safari-media-e2e-final.json`
- `/tmp/safari-erp-regression-final.json`
- `/tmp/safari-media-persistence-final.log`
- `/tmp/safari-media-runtime-proof-ywa2k2/RESULTS.md`
- `e2e/test-results` and `/tmp/safari-erp-regression-final-artifacts`

The browser suites were run by the ERP agent; their machine-readable results
were independently parsed, not represented as reviewer-executed Playwright.
The reviewer viewed genuine private-tour pixels and the Arabic 360px category
capture. Normal upload/rights/revocation, small replacement, four-language alt,
four viewport sizes, keyboard/Axe checks, replay and real stale-list conflict
cases pass. These checks do not replace owner UAT or comprehensive performance
and accessibility acceptance.

The reviewer independently ran `docker exec wego-safari-media-final-backend-1
sha256sum /app/application.jar` before and after recreation. Current artifact:

```text
04c18ee1765d0506fec02d8ef6f3639881430e92023d940cdea540d7ca41c1ad
```

The approved public image was independently fetched before and after root's
backend/edge recreation at
`/media/categories/DESERT/d713a2d0-2481-4896-9e23-b66aeeba3b6e.jpg`:

```text
SHA256 bb18ae1044375151d177b9ab6036a21bd919bf35138a82f176b325e0077cd4d5
```

Public category metadata exposes only category/path/width/height/alt. Read-only
live checks also confirmed staff-preview 401 on the staff origin, public-origin
staff-route 404, unknown managed bytes 404/no-store/private, invalid locale 400
and encoded optimizer denial. The site has no media-volume mount; the edge has
only its read-only configuration bind. Artifact inspection confirms V29/media
controllers in Safari and no Safari migrations in generic backend resources.

## Gates not accepted or waived

- **P0: consistent database + media backup/restore bundle is unfinished.** A
  manifest/checksum-backed restore must prove recovery of referenced originals
  and derivatives, reject missing/corrupt media and record recovery timing.
  Current DB-only scripts fail closed for V29; the restored-schema guard also
  rejects omitted-media metadata. `/tmp/safari-media-restore-refusal.log` was
  inspected. Refusal is not a completed media-inclusive restore.
- **Release: ordinary backend Docker build remains blocked by plugin-download
  DNS/network failure**, recorded in `/tmp/safari-media-ordinary-backend-build.log`.
  The tested current-JAR/pinned-runtime diagnostic image is not a successful
  ordinary production image build.
- **Release: dependency audit still reports two HIGH findings**, node-forge and
  braces, in `/tmp/safari-media-audit.log`. No exemption or CI bypass is accepted.
- Full-packet final Tier 1 acceptance, remaining performance/owner UAT gates,
  rights-backed legacy asset import and operational follow-ups remain explicit
  on the Board/specification/handoff. No bulk rights approval or commercial
  facts are inferred from this checkpoint.

This review made no application-source edits. Its only repository write is this
new owned review file. No commit, push, deployment, live account/secrets access,
production mutation, broad Docker cleanup or changes to another client stack
were performed or authorized by this report. Temporary evidence must be retained
or archived appropriately before its local paths are cleaned up.
