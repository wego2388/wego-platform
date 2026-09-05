-- A DRAFT run has no external consequence yet — it can be discarded
-- freely. Posting is terminal: it creates a real journal entry (see
-- journal_entry_id) and the run itself is never edited again, matching
-- accounting_journal_entry's own permanent-once-posted discipline.
CREATE TABLE wego.payroll_run (
    id uuid PRIMARY KEY,
    pay_period_start date NOT NULL,
    pay_period_end date NOT NULL,
    currency_code varchar(3) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL,
    posted_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    posted_at timestamp with time zone,
    -- ON DELETE RESTRICT, not CASCADE: a posted payroll run's journal
    -- entry is real financial history and must never be silently
    -- deleted as a side effect of something else.
    journal_entry_id uuid REFERENCES wego.accounting_journal_entry (id) ON DELETE RESTRICT,
    CONSTRAINT payroll_run_period_valid
        CHECK (pay_period_end >= pay_period_start),
    CONSTRAINT payroll_run_status_known
        CHECK (status IN ('DRAFT', 'POSTED')),
    CONSTRAINT payroll_run_currency_code_format
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT payroll_run_posted_fields_match_status
        CHECK (
            (status = 'POSTED' AND posted_at IS NOT NULL AND journal_entry_id IS NOT NULL)
            OR (status = 'DRAFT' AND posted_at IS NULL AND journal_entry_id IS NULL)
        )
);

CREATE INDEX payroll_run_period_idx
    ON wego.payroll_run (pay_period_start, pay_period_end);

CREATE INDEX payroll_run_status_idx
    ON wego.payroll_run (status);

CREATE TABLE wego.payroll_line (
    id uuid PRIMARY KEY,
    payroll_run_id uuid NOT NULL REFERENCES wego.payroll_run (id) ON DELETE CASCADE,
    -- ON DELETE RESTRICT: an employee record is never deleted in this
    -- platform (only terminated — see hr_employee), so this can never
    -- actually fire; it documents the real intent (payroll history must
    -- outlive nothing) rather than silently allowing it.
    employee_id uuid NOT NULL REFERENCES wego.hr_employee (id) ON DELETE RESTRICT,
    amount numeric(12, 2) NOT NULL,
    CONSTRAINT payroll_line_amount_positive
        CHECK (amount > 0),
    CONSTRAINT payroll_line_employee_unique_per_run
        UNIQUE (payroll_run_id, employee_id)
);

CREATE INDEX payroll_line_run_idx
    ON wego.payroll_line (payroll_run_id);

CREATE INDEX payroll_line_employee_idx
    ON wego.payroll_line (employee_id);

COMMENT ON TABLE wego.payroll_run IS
    'A payroll run for one pay period. DRAFT is a real, discardable preview — nothing external depends on it until posted, which creates a real, permanent journal entry.';
COMMENT ON TABLE wego.payroll_line IS
    'One employee''s pay within a payroll run — a snapshot of their base salary at run-creation time, not a live reference (a later salary change never rewrites payroll history).';

-- New payroll permissions, following V9/V10/V11/V12's catalog discipline.
-- Real separation of duties, matching V12's own accounting precedent:
-- only accountant/platform-admin can actually post payroll (it creates a
-- real journal entry), but hr-manager and operations-manager can see it.
INSERT INTO wego.identity_permission (code, description) VALUES
    ('payroll:view', 'View payroll runs.'),
    ('payroll:manage', 'Create, post, and discard payroll runs.');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'payroll:view'),
    ('platform-admin', 'payroll:manage'),
    ('accountant', 'payroll:view'),
    ('accountant', 'payroll:manage'),
    ('hr-manager', 'payroll:view'),
    ('operations-manager', 'payroll:view');
