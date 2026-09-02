CREATE TABLE wego.travel_provider (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    contact_email varchar(320),
    contact_phone varchar(32),
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamp with time zone NOT NULL,
    archived_at timestamp with time zone,
    CONSTRAINT travel_provider_name_not_blank
        CHECK (length(trim(name)) > 0),
    CONSTRAINT travel_provider_contact_present
        CHECK (contact_email IS NOT NULL OR contact_phone IS NOT NULL),
    CONSTRAINT travel_provider_status_known
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT travel_provider_archived_state
        CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL))
);

CREATE TABLE wego.travel_category (
    id uuid PRIMARY KEY,
    code varchar(64) NOT NULL,
    name_en text NOT NULL,
    name_ar text NOT NULL,
    description_en text,
    description_ar text,
    display_order integer NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamp with time zone NOT NULL,
    archived_at timestamp with time zone,
    CONSTRAINT travel_category_code_unique UNIQUE (code),
    CONSTRAINT travel_category_code_format
        CHECK (code ~ '^[a-z][a-z0-9-]*$'),
    CONSTRAINT travel_category_name_en_not_blank
        CHECK (length(trim(name_en)) > 0),
    CONSTRAINT travel_category_name_ar_not_blank
        CHECK (length(trim(name_ar)) > 0),
    CONSTRAINT travel_category_description_pair
        CHECK ((description_en IS NULL) = (description_ar IS NULL)),
    CONSTRAINT travel_category_display_order_nonnegative
        CHECK (display_order >= 0),
    CONSTRAINT travel_category_status_known
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT travel_category_archived_state
        CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL))
);

CREATE INDEX travel_category_status_idx
    ON wego.travel_category (status);

CREATE TABLE wego.travel_service (
    id uuid PRIMARY KEY,
    category_id uuid NOT NULL REFERENCES wego.travel_category (id),
    name_en text NOT NULL,
    name_ar text NOT NULL,
    description_en text NOT NULL,
    description_ar text NOT NULL,
    fulfilment_model varchar(16) NOT NULL,
    provider_id uuid REFERENCES wego.travel_provider (id),
    confirmation_type varchar(16) NOT NULL,
    cancellation_policy_en text NOT NULL,
    cancellation_policy_ar text NOT NULL,
    pickup_info_en text,
    pickup_info_ar text,
    inclusions_en text,
    inclusions_ar text,
    exclusions_en text,
    exclusions_ar text,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    created_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    archived_at timestamp with time zone,
    CONSTRAINT travel_service_name_en_not_blank
        CHECK (length(trim(name_en)) > 0),
    CONSTRAINT travel_service_name_ar_not_blank
        CHECK (length(trim(name_ar)) > 0),
    CONSTRAINT travel_service_description_en_not_blank
        CHECK (length(trim(description_en)) > 0),
    CONSTRAINT travel_service_description_ar_not_blank
        CHECK (length(trim(description_ar)) > 0),
    CONSTRAINT travel_service_cancellation_policy_en_not_blank
        CHECK (length(trim(cancellation_policy_en)) > 0),
    CONSTRAINT travel_service_cancellation_policy_ar_not_blank
        CHECK (length(trim(cancellation_policy_ar)) > 0),
    CONSTRAINT travel_service_fulfilment_model_known
        CHECK (fulfilment_model IN ('DIRECT', 'PARTNER')),
    CONSTRAINT travel_service_provider_pairing
        CHECK ((fulfilment_model = 'PARTNER') = (provider_id IS NOT NULL)),
    CONSTRAINT travel_service_confirmation_type_known
        CHECK (confirmation_type IN ('INSTANT', 'STAFF_REVIEW')),
    CONSTRAINT travel_service_pickup_pair
        CHECK ((pickup_info_en IS NULL) = (pickup_info_ar IS NULL)),
    CONSTRAINT travel_service_inclusions_pair
        CHECK ((inclusions_en IS NULL) = (inclusions_ar IS NULL)),
    CONSTRAINT travel_service_exclusions_pair
        CHECK ((exclusions_en IS NULL) = (exclusions_ar IS NULL)),
    CONSTRAINT travel_service_status_known
        CHECK (status IN ('DRAFT', 'REVIEW', 'APPROVED', 'PUBLISHED', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT travel_service_archived_state
        CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL)),
    CONSTRAINT travel_service_published_state
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX travel_service_category_idx
    ON wego.travel_service (category_id);

CREATE INDEX travel_service_provider_idx
    ON wego.travel_service (provider_id)
    WHERE provider_id IS NOT NULL;

CREATE INDEX travel_service_status_idx
    ON wego.travel_service (status);

CREATE TABLE wego.travel_service_option (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL REFERENCES wego.travel_service (id) ON DELETE CASCADE,
    label_en text NOT NULL,
    label_ar text NOT NULL,
    duration_minutes integer,
    max_participants integer NOT NULL,
    price_amount numeric(10, 2) NOT NULL,
    price_currency varchar(3) NOT NULL,
    price_basis varchar(16) NOT NULL,
    CONSTRAINT travel_service_option_label_en_not_blank
        CHECK (length(trim(label_en)) > 0),
    CONSTRAINT travel_service_option_label_ar_not_blank
        CHECK (length(trim(label_ar)) > 0),
    CONSTRAINT travel_service_option_duration_positive
        CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    CONSTRAINT travel_service_option_max_participants_positive
        CHECK (max_participants > 0),
    CONSTRAINT travel_service_option_price_nonnegative
        CHECK (price_amount >= 0),
    CONSTRAINT travel_service_option_price_basis_known
        CHECK (price_basis IN ('PER_PERSON', 'PER_GROUP', 'PER_VEHICLE', 'FLAT'))
);

CREATE INDEX travel_service_option_service_idx
    ON wego.travel_service_option (service_id);

CREATE TABLE wego.travel_service_media (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL REFERENCES wego.travel_service (id) ON DELETE CASCADE,
    asset_reference text NOT NULL,
    rights_evidence text NOT NULL,
    locale varchar(8) NOT NULL,
    CONSTRAINT travel_service_media_asset_reference_not_blank
        CHECK (length(trim(asset_reference)) > 0),
    CONSTRAINT travel_service_media_rights_evidence_not_blank
        CHECK (length(trim(rights_evidence)) > 0),
    CONSTRAINT travel_service_media_locale_not_blank
        CHECK (length(trim(locale)) > 0)
);

CREATE INDEX travel_service_media_service_idx
    ON wego.travel_service_media (service_id);

-- One generic audit table for all three catalog aggregates (Provider,
-- Category, Service) rather than one per aggregate — a deliberate scope
-- simplification for this phase: catalog master data is lower-risk than
-- WEGO-002's financial booking events, which is what justified that
-- product's per-aggregate audit tables with structured correlation-id
-- propagation. Revisit if this module ever needs outbox/event integration.
CREATE TABLE wego.travel_marketplace_audit_event (
    id uuid PRIMARY KEY,
    aggregate_type varchar(16) NOT NULL,
    aggregate_id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    detail text,
    CONSTRAINT travel_marketplace_audit_event_aggregate_type_known
        CHECK (aggregate_type IN ('PROVIDER', 'CATEGORY', 'SERVICE')),
    CONSTRAINT travel_marketplace_audit_event_type_known
        CHECK (event_type IN ('CREATED', 'UPDATED', 'ARCHIVED', 'SUBMITTED_FOR_REVIEW', 'APPROVED', 'PUBLISHED', 'SUSPENDED'))
);

CREATE INDEX travel_marketplace_audit_event_aggregate_idx
    ON wego.travel_marketplace_audit_event (aggregate_type, aggregate_id);

COMMENT ON TABLE wego.travel_service IS
    'Marketplace catalog aggregate. Publication is gated on price/capacity (travel_service_option), cancellation wording, and rights-cleared media (travel_service_media) — see Service.publish() for the enforced invariant.';

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'service:view'),
    ('platform-admin', 'service:manage'),
    ('platform-admin', 'provider:view'),
    ('platform-admin', 'provider:manage');
