-- ============================================================
-- V23 — Tours Operator: localized tour content, facts and media
-- ============================================================
--
-- Every content document exists as a DRAFT and (once approved) a PUBLISHED
-- copy. Staff edit the DRAFT; publishing copies it to PUBLISHED. The public
-- site reads PUBLISHED only, so editing never removes live content and
-- nothing unapproved reaches customers. The V16 name_*/description_* columns
-- stay unused.

-- Per-locale text: name, descriptions, includes/excludes, know-before-you-go,
-- meeting point and the localized names of the itinerary stops.
CREATE TABLE wego.tours_operator_tour_content (
    tour_id             uuid        NOT NULL
                            REFERENCES wego.tours_operator_tour (id) ON DELETE CASCADE,
    locale              varchar(2)  NOT NULL,
    stage               varchar(16) NOT NULL,
    document            jsonb       NOT NULL,
    updated_at          timestamp with time zone NOT NULL,
    updated_by_user_id  uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,

    PRIMARY KEY (tour_id, locale, stage),
    CONSTRAINT tours_operator_tour_content_locale_known
        CHECK (locale IN ('en', 'ar', 'ru', 'it')),
    CONSTRAINT tours_operator_tour_content_stage_known
        CHECK (stage IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT tours_operator_tour_content_document_object
        CHECK (jsonb_typeof(document) = 'object')
);

-- Locale-independent facts: children allowed, minimum age, guide languages,
-- hotel pickup and the itinerary stops with coordinates.
CREATE TABLE wego.tours_operator_tour_facts (
    tour_id             uuid        NOT NULL
                            REFERENCES wego.tours_operator_tour (id) ON DELETE CASCADE,
    stage               varchar(16) NOT NULL,
    document            jsonb       NOT NULL,
    updated_at          timestamp with time zone NOT NULL,
    updated_by_user_id  uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,

    PRIMARY KEY (tour_id, stage),
    CONSTRAINT tours_operator_tour_facts_stage_known
        CHECK (stage IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT tours_operator_tour_facts_document_object
        CHECK (jsonb_typeof(document) = 'object')
);

-- Media carry their own rights approval: only APPROVED media is public.
CREATE TABLE wego.tours_operator_tour_media (
    id                  uuid        PRIMARY KEY,
    tour_id             uuid        NOT NULL
                            REFERENCES wego.tours_operator_tour (id) ON DELETE CASCADE,
    position            integer     NOT NULL,
    path                varchar(300) NOT NULL,
    width               integer     NOT NULL,
    height              integer     NOT NULL,
    is_cover            boolean     NOT NULL DEFAULT false,
    alt                 jsonb       NOT NULL DEFAULT '{}'::jsonb,
    rights_status       varchar(16) NOT NULL,
    approved_at         timestamp with time zone,
    approved_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at          timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_tour_media_position_unique UNIQUE (tour_id, position),
    CONSTRAINT tours_operator_tour_media_position_nonnegative CHECK (position >= 0),
    CONSTRAINT tours_operator_tour_media_dimensions_positive CHECK (width > 0 AND height > 0),
    CONSTRAINT tours_operator_tour_media_path_format
        CHECK (path ~ '^/media/tours/[a-z0-9-]+/[a-z0-9._-]+\.(avif|webp|jpg|jpeg|png)$'),
    CONSTRAINT tours_operator_tour_media_rights_known
        CHECK (rights_status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT tours_operator_tour_media_approval_matches_status
        CHECK ((rights_status = 'APPROVED') = (approved_at IS NOT NULL)),
    CONSTRAINT tours_operator_tour_media_alt_object
        CHECK (jsonb_typeof(alt) = 'object')
);

-- At most one cover image per tour.
CREATE UNIQUE INDEX tours_operator_tour_media_one_cover
    ON wego.tours_operator_tour_media (tour_id)
    WHERE is_cover;

COMMENT ON TABLE wego.tours_operator_tour_content IS
    'Localized tour content documents. DRAFT is edited by staff; PUBLISHED is a copy made on approval and is the only stage served publicly.';
COMMENT ON TABLE wego.tours_operator_tour_facts IS
    'Locale-independent tour facts and itinerary stops. Same DRAFT/PUBLISHED model as tour content.';
COMMENT ON TABLE wego.tours_operator_tour_media IS
    'Tour images with per-item rights approval and localized alt text. Only APPROVED rows are public.';

-- ── Permissions ───────────────────────────────────────────────────────────────

INSERT INTO wego.identity_permission (code, description) VALUES
    ('tours-operator.content:publish', 'Publish tour content, facts and approve media rights for the public site');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'tours-operator.content:publish');
