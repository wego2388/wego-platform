-- ============================================================
-- V29 — Tours Operator: asset registry and category media
-- ============================================================
--
-- A managed asset is an immutable server-assigned file produced from an
-- uploaded original. Staff upload a source file; the server validates it,
-- strips metadata, re-encodes it, and produces one or more derived sizes.
-- The asset record holds the server-generated paths and dimensions; the
-- caller never supplies paths or dimensions directly.
--
-- Category media uses the same rights model as tour media (DRAFT/APPROVED)
-- so a category cover image also needs editorial approval before it is public.

-- ── Asset registry ────────────────────────────────────────────────────────

CREATE TABLE wego.tours_operator_asset (
    id                  uuid            PRIMARY KEY,
    -- Owning entity type: 'tour_media' or 'category_media'.
    owner_type          varchar(32)     NOT NULL,
    -- UUID of the owning tour_id or category code (stored as text for categories).
    owner_ref           varchar(64)     NOT NULL,
    -- Server-generated stable file name (UUID-based, no original filename).
    storage_key         varchar(300)    NOT NULL UNIQUE,
    -- MIME type detected from file header, not from Content-Type.
    mime_type           varchar(32)     NOT NULL,
    original_width      integer         NOT NULL,
    original_height     integer         NOT NULL,
    file_size_bytes     bigint          NOT NULL,
    sha256              char(64)        NOT NULL,
    upload_request_id   uuid            NOT NULL,
    CONSTRAINT tours_operator_asset_upload_request_unique
        UNIQUE (owner_type, owner_ref, upload_request_id),
    CONSTRAINT tours_operator_asset_owner_known
        CHECK (owner_type IN ('tour_media', 'category_media')),
    CONSTRAINT tours_operator_asset_mime_known
        CHECK (mime_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT tours_operator_asset_dimensions_positive
        CHECK (original_width > 0 AND original_height > 0),
    CONSTRAINT tours_operator_asset_size_positive
        CHECK (file_size_bytes > 0),
    CONSTRAINT tours_operator_asset_sha256_hex
        CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX tours_operator_asset_owner
    ON wego.tours_operator_asset (owner_type, owner_ref);

-- ── Asset variants ────────────────────────────────────────────────────────

-- Each uploaded image produces a set of bounded re-encoded variants.
-- The base variant is the re-encoded original (EXIF stripped, orientation
-- applied, quality normalised). Mobile variants are derived from it.
CREATE TABLE wego.tours_operator_asset_variant (
    asset_id            uuid            NOT NULL
                            REFERENCES wego.tours_operator_asset (id) ON DELETE CASCADE,
    -- 'base' | 'w360' | 'w768' | 'w1024' | 'w1440'
    variant             varchar(16)     NOT NULL,
    storage_key         varchar(300)    NOT NULL UNIQUE,
    width               integer         NOT NULL,
    height              integer         NOT NULL,
    file_size_bytes     bigint          NOT NULL,

    PRIMARY KEY (asset_id, variant),
    CONSTRAINT tours_operator_asset_variant_known
        CHECK (variant IN ('base', 'w360', 'w768', 'w1024', 'w1440')),
    CONSTRAINT tours_operator_asset_variant_dimensions_positive
        CHECK (width > 0 AND height > 0),
    CONSTRAINT tours_operator_asset_variant_size_positive
        CHECK (file_size_bytes > 0)
);

-- ── Category media ────────────────────────────────────────────────────────

-- One cover image per category. Same rights model as tour media.
CREATE TABLE wego.tours_operator_category_media (
    category            varchar(64)     PRIMARY KEY,
    asset_id            uuid
                            REFERENCES wego.tours_operator_asset (id) ON DELETE SET NULL,
    alt                 jsonb           NOT NULL DEFAULT '{}'::jsonb,
    rights_status       varchar(16)     NOT NULL DEFAULT 'DRAFT',
    approved_at         timestamp with time zone,
    approved_by_user_id uuid            REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    updated_at          timestamp with time zone NOT NULL,
    updated_by_user_id  uuid            REFERENCES wego.identity_user (id) ON DELETE SET NULL,

    CONSTRAINT tours_operator_category_media_rights_known
        CHECK (rights_status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT tours_operator_category_media_category_known
        CHECK (category IN ('DESERT', 'SEA', 'CULTURAL', 'SHOWS', 'TRANSFERS')),
    CONSTRAINT tours_operator_category_media_approved_asset_present
        CHECK (rights_status <> 'APPROVED' OR asset_id IS NOT NULL),
    CONSTRAINT tours_operator_category_media_approval_matches_status
        CHECK ((rights_status = 'APPROVED') = (approved_at IS NOT NULL)),
    CONSTRAINT tours_operator_category_media_alt_object
        CHECK (jsonb_typeof(alt) = 'object')
);

-- The five existing TourCategory values, not a new commercial taxonomy.
-- Flyway executes this version exactly once. Plain VALUES also works in
-- jOOQ's DDLDatabase simulation; no PostgreSQL ON CONFLICT is needed here.
INSERT INTO wego.tours_operator_category_media (category, rights_status, updated_at) VALUES
    ('DESERT', 'DRAFT', CURRENT_TIMESTAMP),
    ('SEA', 'DRAFT', CURRENT_TIMESTAMP),
    ('CULTURAL', 'DRAFT', CURRENT_TIMESTAMP),
    ('SHOWS', 'DRAFT', CURRENT_TIMESTAMP),
    ('TRANSFERS', 'DRAFT', CURRENT_TIMESTAMP);

INSERT INTO wego.identity_permission (code, description) VALUES
    ('tours-operator.media:upload', 'Upload and manage tour/category images in the asset registry');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'tours-operator.media:upload');

COMMENT ON TABLE wego.tours_operator_asset IS
    'Immutable asset records for uploaded and re-encoded images. Paths are server-generated; callers never supply them.';
COMMENT ON TABLE wego.tours_operator_asset_variant IS
    'Derived re-encoded sizes for each asset. The base variant is the re-encoded original with EXIF stripped.';
COMMENT ON TABLE wego.tours_operator_category_media IS
    'One cover image per tour category with the same DRAFT/APPROVED rights model as tour media.';
