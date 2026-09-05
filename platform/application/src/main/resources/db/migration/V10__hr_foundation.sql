CREATE TABLE wego.hr_employee (
    id uuid PRIMARY KEY,
    full_name text NOT NULL,
    position text NOT NULL,
    department text,
    hire_date date NOT NULL,
    email varchar(320),
    phone varchar(32),
    -- A pricing-snapshot-style pair, same discipline as divers_offering's
    -- unit_price/currency_code: both null (no agreed salary yet) or both
    -- set, never one without the other.
    base_salary_amount numeric(10, 2),
    base_salary_currency_code varchar(3),
    -- Optional: not every employee needs a platform login. ON DELETE SET
    -- NULL, not RESTRICT — an identity account being removed must never
    -- block deleting that account, and an employee record outliving its
    -- linked login is a real, unremarkable state (see Employee.kt).
    linked_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL,
    terminated_at timestamp with time zone,
    CONSTRAINT hr_employee_full_name_not_blank
        CHECK (length(trim(full_name)) > 0),
    CONSTRAINT hr_employee_position_not_blank
        CHECK (length(trim(position)) > 0),
    CONSTRAINT hr_employee_status_known
        CHECK (status IN ('ACTIVE', 'TERMINATED')),
    CONSTRAINT hr_employee_terminated_at_matches_status
        CHECK (
            (status = 'TERMINATED' AND terminated_at IS NOT NULL)
            OR (status = 'ACTIVE' AND terminated_at IS NULL)
        ),
    CONSTRAINT hr_employee_base_salary_pair
        CHECK ((base_salary_amount IS NULL) = (base_salary_currency_code IS NULL)),
    CONSTRAINT hr_employee_base_salary_nonnegative
        CHECK (base_salary_amount IS NULL OR base_salary_amount >= 0),
    CONSTRAINT hr_employee_base_salary_currency_code_format
        CHECK (base_salary_currency_code IS NULL OR base_salary_currency_code ~ '^[A-Z]{3}$')
);

CREATE INDEX hr_employee_status_idx
    ON wego.hr_employee (status);

CREATE INDEX hr_employee_linked_user_idx
    ON wego.hr_employee (linked_user_id)
    WHERE linked_user_id IS NOT NULL;

CREATE TABLE wego.hr_employee_audit_event (
    id uuid PRIMARY KEY,
    employee_id uuid NOT NULL REFERENCES wego.hr_employee (id) ON DELETE CASCADE,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    reason text,
    correlation_id uuid,
    CONSTRAINT hr_employee_audit_event_type_known
        CHECK (event_type IN ('EMPLOYEE_CREATED', 'EMPLOYEE_UPDATED', 'EMPLOYEE_TERMINATED'))
);

CREATE INDEX hr_employee_audit_event_employee_idx
    ON wego.hr_employee_audit_event (employee_id);

COMMENT ON TABLE wego.hr_employee IS
    'A real employee record, distinct from an identity_user login account — see linked_user_id. Salary/contact PII is not redacted on termination (unlike divers_diver): it remains a real, ongoing accounting/audit need.';
COMMENT ON TABLE wego.hr_employee_audit_event IS
    'Append-only record of employee lifecycle mutations, same shape as every other product module''s own audit-event table.';

-- New HR administration permissions, following V9's catalog discipline.
INSERT INTO wego.identity_permission (code, description) VALUES
    ('hr:employee-view', 'View the employee roster and individual employee records.'),
    ('hr:employee-manage', 'Create, update, and terminate employee records.');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'hr:employee-view'),
    ('platform-admin', 'hr:employee-manage'),
    -- hr-manager already handles identity:user-* (staff accounts); employee
    -- records are the same real job, just a different table.
    ('hr-manager', 'hr:employee-view'),
    ('hr-manager', 'hr:employee-manage'),
    ('operations-manager', 'hr:employee-view'),
    ('operations-manager', 'hr:employee-manage');
