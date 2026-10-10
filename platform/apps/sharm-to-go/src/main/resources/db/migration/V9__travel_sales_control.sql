-- Public intake only. Existing requests, catalog and staff operations keep working.
-- A seeded singleton is the transaction coordination point: request creation
-- holds FOR SHARE until commit; changing intake holds FOR UPDATE until commit.
CREATE TABLE wego.travel_sales_control (
    id smallint PRIMARY KEY CHECK (id = 1),
    requests_paused boolean NOT NULL,
    version bigint NOT NULL CHECK (version >= 0),
    reason varchar(300),
    updated_by_user_id uuid REFERENCES wego.identity_user(id) ON DELETE SET NULL,
    updated_at timestamp with time zone
);

INSERT INTO wego.travel_sales_control (id, requests_paused, version) VALUES (1, false, 0);

-- Insert-only application history, written in the same transaction as the flag.
-- No customer data or public message is stored here; reason is staff-only.
CREATE TABLE wego.travel_sales_control_event (
    version bigint PRIMARY KEY CHECK (version > 0),
    previously_paused boolean NOT NULL,
    requests_paused boolean NOT NULL,
    reason varchar(300),
    actor_user_id uuid REFERENCES wego.identity_user(id) ON DELETE SET NULL,
    occurred_at timestamp with time zone NOT NULL
);

INSERT INTO wego.identity_permission (code, description) VALUES
    ('travel-sales:manage', 'Pause or resume new public travel requests.');
INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'travel-sales:manage');
