CREATE TABLE wego.divers_diver (
    id uuid PRIMARY KEY,
    full_name text NOT NULL,
    nationality varchar(100),
    primary_language varchar(100),
    email varchar(320),
    phone varchar(32),
    emergency_contact_name text,
    emergency_contact_phone varchar(32),
    -- Free-text, staff-entered. Never a structured medical clearance
    -- decision and never scored — dive-fitness judgment stays with a
    -- certified instructor, not this system.
    medical_notes text,
    total_logged_dives integer NOT NULL DEFAULT 0,
    max_depth_meters numeric(5, 1),
    last_dive_on date,
    bcd_size varchar(16),
    fin_size varchar(16),
    wetsuit_size varchar(16),
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL,
    archived_at timestamp with time zone,
    CONSTRAINT divers_diver_full_name_not_blank
        CHECK (length(trim(full_name)) > 0),
    CONSTRAINT divers_diver_contact_present
        CHECK (
            (email IS NOT NULL AND length(trim(email)) > 0)
            OR (phone IS NOT NULL AND length(trim(phone)) > 0)
        ),
    CONSTRAINT divers_diver_status_known
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT divers_diver_archived_at_matches_status
        CHECK (
            (status = 'ARCHIVED' AND archived_at IS NOT NULL)
            OR (status = 'ACTIVE' AND archived_at IS NULL)
        ),
    CONSTRAINT divers_diver_total_logged_dives_nonnegative
        CHECK (total_logged_dives >= 0),
    CONSTRAINT divers_diver_max_depth_nonnegative
        CHECK (max_depth_meters IS NULL OR max_depth_meters >= 0)
);

CREATE INDEX divers_diver_active_idx
    ON wego.divers_diver (status)
    WHERE status = 'ACTIVE';

CREATE TABLE wego.divers_diver_certification (
    id uuid PRIMARY KEY,
    diver_id uuid NOT NULL REFERENCES wego.divers_diver (id) ON DELETE CASCADE,
    -- Free text, not an enum: real agencies span PADI/SSI/CMAS/TDI/RAID/
    -- national federations, and a closed list would reject a real
    -- certification on day one.
    agency text NOT NULL,
    certification_level text NOT NULL,
    certification_number varchar(64),
    issued_on date,
    CONSTRAINT divers_diver_certification_agency_not_blank
        CHECK (length(trim(agency)) > 0),
    CONSTRAINT divers_diver_certification_level_not_blank
        CHECK (length(trim(certification_level)) > 0)
);

CREATE INDEX divers_diver_certification_diver_idx
    ON wego.divers_diver_certification (diver_id);

CREATE TABLE wego.divers_diver_audit_event (
    id uuid PRIMARY KEY,
    diver_id uuid NOT NULL REFERENCES wego.divers_diver (id) ON DELETE CASCADE,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    correlation_id uuid,
    CONSTRAINT divers_diver_audit_event_type_known
        CHECK (event_type IN ('DIVER_CREATED', 'DIVER_UPDATED', 'DIVER_ARCHIVED'))
);

CREATE INDEX divers_diver_audit_event_diver_idx
    ON wego.divers_diver_audit_event (diver_id);

COMMENT ON TABLE wego.divers_diver IS
    'A real diver profile: certifications, dive-history summary, medical/emergency contact, equipment sizing. Staff-managed records only — never an automated risk score or a safety-clearance decision.';
COMMENT ON TABLE wego.divers_diver_certification IS
    'One certification record per diver per issuing agency/level. A diver may hold several.';
COMMENT ON TABLE wego.divers_diver_audit_event IS
    'Append-only record of diver-profile lifecycle changes.';

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'diver:manage'),
    ('platform-admin', 'diver:view');
