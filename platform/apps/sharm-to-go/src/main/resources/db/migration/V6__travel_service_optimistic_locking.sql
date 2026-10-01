-- Codex review finding (clients/sharm-to-go/handoff/2026-10-01_CODEX_REVIEW_FINDINGS_BACKLOG.md):
-- UpdateServiceService replaced the full service record without checking
-- whether it had changed since the editor loaded it, so two staff editing
-- the same service concurrently meant the second save silently discarded
-- the first's changes. `version` is bumped on every content edit
-- (UpdateServiceService.update, via Service.withUpdatedDetails); the staff
-- editor must send back the version it loaded, and a mismatch is rejected
-- rather than overwritten.
ALTER TABLE wego.travel_service
    ADD COLUMN version integer NOT NULL DEFAULT 1;
