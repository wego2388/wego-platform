-- Continues this app's own migration sequence (V1-V3) with the generic
-- identity-administration schema origin/main's WEGO-012 added to the Divers
-- app as V9__identity_administration.sql. Ported here, not shared, per
-- Packet 0R's own accepted-duplication precedent for V1/V2 — this app has
-- no shared migration folder with :platform:application.
--
-- Deliberately NOT a verbatim copy: WEGO-012's version seeds Divers-specific
-- permissions (offering/booking/diver/equipment/boat-charter/course) and
-- dive-shop-shaped staff roles (operations-manager, front-desk, accountant,
-- hr-manager, instructor) that have no meaning for this app and no
-- Divers code on this app's classpath to enforce them. Only the truly
-- generic identity-administration infrastructure is ported, seeded with
-- this app's own real permission codes (identity:* plus the
-- service:*/provider:* pair from V3). No new staff role is invented here —
-- "who are Sharm To Go's real first staff users, and should their
-- permissions be scoped separately from platform-admin" is still the
-- explicit open owner decision TECHNICAL_EXECUTION_PLAN.md's "What the
-- owner supplies" section already tracks; inventing roles to make this
-- migration look complete would preempt that real decision.

CREATE TABLE wego.identity_permission (
    code varchar(128) PRIMARY KEY,
    description text NOT NULL,
    CONSTRAINT identity_permission_code_not_blank
        CHECK (length(trim(code)) > 0),
    CONSTRAINT identity_permission_description_not_blank
        CHECK (length(trim(description)) > 0)
);

INSERT INTO wego.identity_permission (code, description) VALUES
    ('identity:administer', 'Full platform administration access (superset of every other permission below).'),
    ('identity:user-view', 'View staff accounts and their assigned roles.'),
    ('identity:user-manage', 'Create, disable, re-enable staff accounts, reset their passwords, and change their roles.'),
    ('identity:role-view', 'View roles and the permissions attached to each.'),
    ('identity:role-manage', 'Create roles and change which permissions a role grants.'),
    ('service:view', 'View travel marketplace services.'),
    ('service:manage', 'Create, update, and manage the publication lifecycle of travel marketplace services.'),
    ('provider:view', 'View travel marketplace providers.'),
    ('provider:manage', 'Create, update, and archive travel marketplace providers.');

ALTER TABLE wego.identity_role_permission
    ADD CONSTRAINT identity_role_permission_code_fk
        FOREIGN KEY (permission_code) REFERENCES wego.identity_permission (code);

-- "platform-admin" already holds identity:administer and the V3 service/
-- provider permissions; only the new administration-surface permissions
-- this migration introduces need granting explicitly.
INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'identity:user-view'),
    ('platform-admin', 'identity:user-manage'),
    ('platform-admin', 'identity:role-view'),
    ('platform-admin', 'identity:role-manage');

UPDATE wego.identity_role
    SET description = 'Full platform administration access — every permission, including staff account and role administration.'
    WHERE code = 'platform-admin';

COMMENT ON TABLE wego.identity_permission IS
    'Registry of every permission code this app actually enforces. identity_role_permission.permission_code is FK-constrained to this table.';

-- Same widening WEGO-012 applied to the Divers app's audit event types —
-- this app's own IdentityAdminController (shared kernel code) writes these
-- same event types regardless of which product it is compiled alongside.
ALTER TABLE wego.identity_audit_event DROP CONSTRAINT identity_audit_event_type_known;
ALTER TABLE wego.identity_audit_event ADD CONSTRAINT identity_audit_event_type_known
    CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'PERMISSION_DENIED',
        'USER_CREATED', 'USER_DISABLED', 'USER_ENABLED', 'USER_PASSWORD_RESET', 'USER_ROLES_CHANGED',
        'ROLE_CREATED', 'ROLE_PERMISSIONS_CHANGED'
    ));
