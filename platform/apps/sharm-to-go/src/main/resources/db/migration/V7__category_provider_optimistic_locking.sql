-- Same gap as V6 (travel_service), found by auditing the other two staff
-- editors that share UpdateServiceService's exact shape: UpdateProviderService
-- and UpdateCategoryService both replaced the full record without checking
-- whether it had changed since the editor loaded it. Lower blast radius than
-- the service editor (no child option/media rows to silently lose), but the
-- same silent-overwrite-of-a-concurrent-edit risk applies equally.
ALTER TABLE wego.travel_provider
    ADD COLUMN version integer NOT NULL DEFAULT 1;

ALTER TABLE wego.travel_category
    ADD COLUMN version integer NOT NULL DEFAULT 1;
