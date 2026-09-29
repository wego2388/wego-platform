-- Safari Tours Sharm owns an isolated identity database. This migration keeps
-- only generic account/role administration permissions; tours-operator
-- permissions are introduced by the selected product migrations below.
CREATE TABLE wego.identity_permission (
    code varchar(128) PRIMARY KEY,
    description text NOT NULL,
    CONSTRAINT identity_permission_code_not_blank
        CHECK (length(trim(code)) > 0),
    CONSTRAINT identity_permission_description_not_blank
        CHECK (length(trim(description)) > 0)
);

INSERT INTO wego.identity_permission (code, description) VALUES
    ('identity:administer', 'Full administration access for this isolated client instance.'),
    ('identity:user-view', 'View staff accounts and their assigned roles.'),
    ('identity:user-manage', 'Create, disable, re-enable and update staff accounts.'),
    ('identity:role-view', 'View roles and their permissions.'),
    ('identity:role-manage', 'Create roles and change role permissions.');

ALTER TABLE wego.identity_role_permission
    ADD CONSTRAINT identity_role_permission_code_fk
        FOREIGN KEY (permission_code) REFERENCES wego.identity_permission (code);

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'identity:user-view'),
    ('platform-admin', 'identity:user-manage'),
    ('platform-admin', 'identity:role-view'),
    ('platform-admin', 'identity:role-manage');

UPDATE wego.identity_role
SET description = 'Full administration access for this isolated client instance.'
WHERE code = 'platform-admin';

ALTER TABLE wego.identity_audit_event DROP CONSTRAINT identity_audit_event_type_known;
ALTER TABLE wego.identity_audit_event ADD CONSTRAINT identity_audit_event_type_known
    CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'PERMISSION_DENIED',
        'USER_CREATED', 'USER_DISABLED', 'USER_ENABLED', 'USER_PASSWORD_RESET', 'USER_ROLES_CHANGED',
        'ROLE_CREATED', 'ROLE_PERMISSIONS_CHANGED'
    ));
