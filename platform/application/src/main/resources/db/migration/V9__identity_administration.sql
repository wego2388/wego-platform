-- Permission catalog: until now, `permission_code` in identity_role_permission
-- was free-text with no registry of what codes actually exist — every valid
-- code only ever appeared as a side effect of being granted to platform-admin
-- in some earlier migration. This table is the single source of truth a
-- role-administration UI can list from, and the FK below closes the typo/drift
-- risk (a role could previously be granted a permission code that matches no
-- real @PreAuthorize check anywhere).
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
    ('offering:manage', 'Create and close offerings (trips, courses, rentals, packages).'),
    ('offering:view', 'View offerings.'),
    ('booking:create', 'Create bookings.'),
    ('booking:view', 'View bookings.'),
    ('booking:cancel', 'Cancel bookings.'),
    ('booking:payment-update', 'Record a booking payment.'),
    ('booking:refund', 'Refund a booking.'),
    ('diver:manage', 'Create, update, and archive diver profiles.'),
    ('diver:view', 'View diver profiles.'),
    ('equipment:manage', 'Manage the equipment/tank registry (retire, maintain, rent, return).'),
    ('equipment:view', 'View the equipment/tank registry.'),
    ('boat-charter:manage', 'Manage boat charters and their offering links.'),
    ('boat-charter:view', 'View boat charters.'),
    ('course:manage', 'Manage course enrollments and their stage progression.'),
    ('course:view', 'View course enrollments.');

ALTER TABLE wego.identity_role_permission
    ADD CONSTRAINT identity_role_permission_code_fk
        FOREIGN KEY (permission_code) REFERENCES wego.identity_permission (code);

-- "platform-admin" already holds identity:administer; that single permission
-- is treated as a superset by PermissionResolver's own check (see
-- IdentityAdminController), so it does not additionally need every granular
-- code listed here. New administration-surface permissions this migration
-- introduces are granted to it explicitly anyway, so a direct query against
-- identity_role_permission (rather than through the superset check) also
-- shows the full, real picture for this role.
INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'identity:user-view'),
    ('platform-admin', 'identity:user-manage'),
    ('platform-admin', 'identity:role-view'),
    ('platform-admin', 'identity:role-manage');

UPDATE wego.identity_role
    SET description = 'Full platform administration access — every permission, including staff account and role administration.'
    WHERE code = 'platform-admin';

-- Real, distinct staff roles for a dive-shop-shaped team, each holding only
-- the permissions its job actually needs — the first roles this platform has
-- ever had besides the single do-everything platform-admin.
INSERT INTO wego.identity_role (code, description) VALUES
    ('operations-manager', 'Runs day-to-day operations: offerings, bookings, divers, equipment, boat charters, courses, and staff accounts — everything except role administration itself.'),
    ('front-desk', 'Handles walk-in and phone bookings: creates/views bookings and offerings, views (not edits) divers and equipment.'),
    ('accountant', 'Handles payments: views bookings and can record payments/refunds, with read-only visibility into offerings.'),
    ('hr-manager', 'Manages staff accounts, matching operations-manager''s people-administration slice without the operational modules.'),
    ('instructor', 'A working dive instructor: views their own course enrollments and the divers/equipment they touch day to day, with no management rights.');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('operations-manager', 'offering:manage'), ('operations-manager', 'offering:view'),
    ('operations-manager', 'booking:create'), ('operations-manager', 'booking:view'),
    ('operations-manager', 'booking:cancel'), ('operations-manager', 'booking:payment-update'), ('operations-manager', 'booking:refund'),
    ('operations-manager', 'diver:manage'), ('operations-manager', 'diver:view'),
    ('operations-manager', 'equipment:manage'), ('operations-manager', 'equipment:view'),
    ('operations-manager', 'boat-charter:manage'), ('operations-manager', 'boat-charter:view'),
    ('operations-manager', 'course:manage'), ('operations-manager', 'course:view'),
    ('operations-manager', 'identity:user-view'), ('operations-manager', 'identity:user-manage'),

    ('front-desk', 'offering:view'),
    ('front-desk', 'booking:create'), ('front-desk', 'booking:view'), ('front-desk', 'booking:cancel'),
    ('front-desk', 'diver:view'),
    ('front-desk', 'equipment:view'),

    ('accountant', 'offering:view'),
    ('accountant', 'booking:view'), ('accountant', 'booking:payment-update'), ('accountant', 'booking:refund'),

    ('hr-manager', 'identity:user-view'), ('hr-manager', 'identity:user-manage'),

    ('instructor', 'course:view'),
    ('instructor', 'diver:view'),
    ('instructor', 'equipment:view'),
    ('instructor', 'boat-charter:view');

COMMENT ON TABLE wego.identity_permission IS
    'Registry of every permission code the platform actually enforces. identity_role_permission.permission_code is FK-constrained to this table.';

-- Every other module in this platform records an audit event for its own mutations; the new
-- account/role administration surface this migration backs must do the same, not be the one
-- exception. Postgres has no ALTER CONSTRAINT, so the CHECK is dropped and recreated widened.
ALTER TABLE wego.identity_audit_event DROP CONSTRAINT identity_audit_event_type_known;
ALTER TABLE wego.identity_audit_event ADD CONSTRAINT identity_audit_event_type_known
    CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'PERMISSION_DENIED',
        'USER_CREATED', 'USER_DISABLED', 'USER_ENABLED', 'USER_PASSWORD_RESET', 'USER_ROLES_CHANGED',
        'ROLE_CREATED', 'ROLE_PERMISSIONS_CHANGED'
    ));
