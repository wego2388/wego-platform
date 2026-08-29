-- Real operating fact confirmed by the owner: boats are chartered, never
-- owned (Barbarossa, 50-passenger license; Al-Horeya, 40-passenger
-- license; ad hoc daily and dive-safari charters as work requires). This
-- is deliberately not a fleet-ops model (no GPS/fuel/engine telemetry) —
-- just a real charter registry with the one safety-relevant rule that
-- matters: a boat trip must never claim more seats than the boat's real
-- licensed passenger capacity.
CREATE TABLE wego.divers_boat_charter (
    id uuid PRIMARY KEY,
    boat_name text NOT NULL,
    charter_type varchar(16) NOT NULL,
    licensed_capacity integer NOT NULL,
    starts_on date NOT NULL,
    ends_on date,
    notes text,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL,
    ended_at timestamp with time zone,
    CONSTRAINT divers_boat_charter_type_known
        CHECK (charter_type IN ('STANDING', 'DAILY', 'SAFARI')),
    CONSTRAINT divers_boat_charter_boat_name_not_blank
        CHECK (length(trim(boat_name)) > 0),
    CONSTRAINT divers_boat_charter_licensed_capacity_positive
        CHECK (licensed_capacity > 0),
    CONSTRAINT divers_boat_charter_ends_on_after_starts_on
        CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT divers_boat_charter_status_known
        CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT divers_boat_charter_ended_at_matches_status
        CHECK (
            (status = 'ENDED' AND ended_at IS NOT NULL)
            OR (status = 'ACTIVE' AND ended_at IS NULL)
        )
);

CREATE INDEX divers_boat_charter_active_idx
    ON wego.divers_boat_charter (status)
    WHERE status = 'ACTIVE';

-- offering_id as the primary key: at most one boat charter per offering,
-- enforced at the database level, not just by application convention.
CREATE TABLE wego.divers_offering_boat_charter (
    offering_id uuid PRIMARY KEY REFERENCES wego.divers_offering (id) ON DELETE CASCADE,
    boat_charter_id uuid NOT NULL REFERENCES wego.divers_boat_charter (id) ON DELETE RESTRICT,
    linked_at timestamp with time zone NOT NULL
);

CREATE INDEX divers_offering_boat_charter_charter_idx
    ON wego.divers_offering_boat_charter (boat_charter_id);

CREATE TABLE wego.divers_boat_charter_audit_event (
    id uuid PRIMARY KEY,
    boat_charter_id uuid NOT NULL REFERENCES wego.divers_boat_charter (id) ON DELETE CASCADE,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    correlation_id uuid,
    CONSTRAINT divers_boat_charter_audit_event_type_known
        CHECK (event_type IN ('CHARTER_CREATED', 'CHARTER_ENDED'))
);

CREATE INDEX divers_boat_charter_audit_event_charter_idx
    ON wego.divers_boat_charter_audit_event (boat_charter_id);

COMMENT ON TABLE wego.divers_boat_charter IS
    'A real charter agreement for one boat — standing (Barbarossa/Al-Horeya style), a single day charter, or a multi-day safari charter. Never a fleet asset: this business charters boats, it does not own them.';
COMMENT ON TABLE wego.divers_offering_boat_charter IS
    'Links a boat-diving offering to the real charter whose licensed capacity governs it. Deliberately not embedded in divers_offering itself — additive, and an offering with no boat trip has no row here.';
COMMENT ON TABLE wego.divers_boat_charter_audit_event IS
    'Append-only record of boat-charter lifecycle changes.';

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'boat-charter:manage'),
    ('platform-admin', 'boat-charter:view');
