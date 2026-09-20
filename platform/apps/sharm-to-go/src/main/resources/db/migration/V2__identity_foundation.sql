CREATE TABLE wego.identity_user (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL,
    password_hash text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamp with time zone NOT NULL,
    failed_login_count integer NOT NULL DEFAULT 0,
    locked_until timestamp with time zone,
    CONSTRAINT identity_user_email_unique UNIQUE (email),
    CONSTRAINT identity_user_email_not_blank
        CHECK (length(trim(email)) > 0),
    CONSTRAINT identity_user_password_hash_not_blank
        CHECK (length(trim(password_hash)) > 0),
    CONSTRAINT identity_user_status_known
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT identity_user_failed_login_count_nonnegative
        CHECK (failed_login_count >= 0)
);

CREATE TABLE wego.identity_role (
    code varchar(64) PRIMARY KEY,
    description text NOT NULL,
    CONSTRAINT identity_role_code_not_blank
        CHECK (length(trim(code)) > 0),
    CONSTRAINT identity_role_description_not_blank
        CHECK (length(trim(description)) > 0)
);

CREATE TABLE wego.identity_role_permission (
    role_code varchar(64) NOT NULL REFERENCES wego.identity_role (code) ON DELETE CASCADE,
    permission_code varchar(128) NOT NULL,
    PRIMARY KEY (role_code, permission_code),
    CONSTRAINT identity_role_permission_code_not_blank
        CHECK (length(trim(permission_code)) > 0)
);

CREATE TABLE wego.identity_user_role (
    user_id uuid NOT NULL REFERENCES wego.identity_user (id) ON DELETE CASCADE,
    role_code varchar(64) NOT NULL REFERENCES wego.identity_role (code) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE wego.identity_session (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES wego.identity_user (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    CONSTRAINT identity_session_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT identity_session_token_hash_not_blank
        CHECK (length(trim(token_hash)) > 0),
    CONSTRAINT identity_session_expires_after_issued
        CHECK (expires_at > issued_at)
);

CREATE INDEX identity_session_user_idx
    ON wego.identity_session (user_id);

CREATE INDEX identity_session_active_idx
    ON wego.identity_session (token_hash)
    WHERE revoked_at IS NULL;

CREATE TABLE wego.identity_audit_event (
    id uuid PRIMARY KEY,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    actor_email varchar(320),
    detail text,
    correlation_id uuid,
    CONSTRAINT identity_audit_event_type_known
        CHECK (event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'PERMISSION_DENIED'))
);

CREATE INDEX identity_audit_event_occurred_at_idx
    ON wego.identity_audit_event (occurred_at);

CREATE INDEX identity_audit_event_actor_idx
    ON wego.identity_audit_event (actor_user_id)
    WHERE actor_user_id IS NOT NULL;

COMMENT ON TABLE wego.identity_user IS
    'Platform user accounts with hashed credentials and basic lockout state.';
COMMENT ON TABLE wego.identity_role IS
    'Named roles assigned to users; permissions attach to a role, not directly to a user.';
COMMENT ON TABLE wego.identity_session IS
    'Active and historical bearer sessions. Only the SHA-256 hash of the raw token is stored.';
COMMENT ON TABLE wego.identity_audit_event IS
    'Append-only record of authentication and authorization decisions.';

INSERT INTO wego.identity_role (code, description) VALUES
    ('platform-admin', 'Full platform administration access during the WEGO-001 foundation.');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'identity:administer');
