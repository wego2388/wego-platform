CREATE TABLE wego.accounting_account (
    id uuid PRIMARY KEY,
    code varchar(20) NOT NULL,
    name text NOT NULL,
    account_type varchar(16) NOT NULL,
    parent_account_id uuid REFERENCES wego.accounting_account (id) ON DELETE SET NULL,
    description text,
    is_active boolean NOT NULL DEFAULT true,
    created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT accounting_account_code_unique UNIQUE (code),
    CONSTRAINT accounting_account_code_not_blank CHECK (length(trim(code)) > 0),
    CONSTRAINT accounting_account_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT accounting_account_type_known
        CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'))
);

CREATE INDEX accounting_account_type_idx
    ON wego.accounting_account (account_type);

CREATE INDEX accounting_account_parent_idx
    ON wego.accounting_account (parent_account_id)
    WHERE parent_account_id IS NOT NULL;

-- Journal entries are permanent once posted — there is deliberately no
-- edit or delete endpoint. A mistake is corrected with a real reversing
-- entry (see reversal_of_entry_id below), the standard accounting
-- practice, matching this codebase's existing preference for append-only
-- history over mutable/deletable financial records.
CREATE TABLE wego.accounting_journal_entry (
    id uuid PRIMARY KEY,
    entry_date date NOT NULL,
    description text NOT NULL,
    reference text,
    currency_code varchar(3) NOT NULL,
    -- Set only on a reversal entry, pointing at the entry it reverses. The
    -- unique index below is the real guard against reversing the same
    -- entry twice.
    reversal_of_entry_id uuid REFERENCES wego.accounting_journal_entry (id) ON DELETE RESTRICT,
    posted_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    posted_at timestamp with time zone NOT NULL,
    correlation_id uuid,
    CONSTRAINT accounting_journal_entry_description_not_blank
        CHECK (length(trim(description)) > 0),
    CONSTRAINT accounting_journal_entry_currency_code_format
        CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE INDEX accounting_journal_entry_date_idx
    ON wego.accounting_journal_entry (entry_date);

CREATE UNIQUE INDEX accounting_journal_entry_reversal_unique
    ON wego.accounting_journal_entry (reversal_of_entry_id)
    WHERE reversal_of_entry_id IS NOT NULL;

-- Each line debits or credits exactly one account. A journal entry
-- balances when the sum of its DEBIT lines equals the sum of its CREDIT
-- lines, in one shared currency — enforced in the application layer
-- (spans multiple rows, not expressible as a single-row CHECK), the same
-- discipline as the idempotency-key concurrency guard elsewhere in this
-- codebase: a real invariant enforced where it actually can be.
CREATE TABLE wego.accounting_journal_line (
    id uuid PRIMARY KEY,
    journal_entry_id uuid NOT NULL REFERENCES wego.accounting_journal_entry (id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES wego.accounting_account (id) ON DELETE RESTRICT,
    direction varchar(6) NOT NULL,
    amount numeric(12, 2) NOT NULL,
    line_order integer NOT NULL,
    CONSTRAINT accounting_journal_line_direction_known
        CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT accounting_journal_line_amount_positive
        CHECK (amount > 0)
);

CREATE INDEX accounting_journal_line_entry_idx
    ON wego.accounting_journal_line (journal_entry_id);

CREATE INDEX accounting_journal_line_account_idx
    ON wego.accounting_journal_line (account_id);

COMMENT ON TABLE wego.accounting_account IS
    'The chart of accounts. Deactivated (not deleted) once referenced by journal history — see is_active.';
COMMENT ON TABLE wego.accounting_journal_entry IS
    'A permanent, immutable double-entry posting. Corrections happen via a real reversing entry, never an edit.';
COMMENT ON TABLE wego.accounting_journal_line IS
    'One debit or credit against one account. A journal entry''s lines must sum to zero (debits == credits) in one shared currency, enforced in the application layer.';

-- New accounting permissions, following V9/V10/V11's catalog discipline.
-- Real separation of duties: operations-manager can see the books but
-- not post to them — only accountant and platform-admin can.
INSERT INTO wego.identity_permission (code, description) VALUES
    ('accounting:coa-view', 'View the chart of accounts.'),
    ('accounting:coa-manage', 'Create, edit, activate, and deactivate accounts.'),
    ('accounting:journal-view', 'View journal entries.'),
    ('accounting:journal-manage', 'Post and reverse journal entries.');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'accounting:coa-view'),
    ('platform-admin', 'accounting:coa-manage'),
    ('platform-admin', 'accounting:journal-view'),
    ('platform-admin', 'accounting:journal-manage'),
    ('accountant', 'accounting:coa-view'),
    ('accountant', 'accounting:coa-manage'),
    ('accountant', 'accounting:journal-view'),
    ('accountant', 'accounting:journal-manage'),
    ('operations-manager', 'accounting:coa-view'),
    ('operations-manager', 'accounting:journal-view');

-- A real, standard starter chart of accounts for a small service business
-- (not fictional dive-shop-specific line items) — a business customizes
-- from here via the real CRUD/deactivate endpoints, the same "seed a
-- sensible default, let staff manage it" discipline V9 already used for
-- roles.
INSERT INTO wego.accounting_account (id, code, name, account_type, is_active, created_at, updated_at) VALUES
    (gen_random_uuid(), '1000', 'Cash on Hand', 'ASSET', true, now(), now()),
    (gen_random_uuid(), '1010', 'Bank Account', 'ASSET', true, now(), now()),
    (gen_random_uuid(), '1200', 'Accounts Receivable', 'ASSET', true, now(), now()),
    (gen_random_uuid(), '2000', 'Accounts Payable', 'LIABILITY', true, now(), now()),
    (gen_random_uuid(), '2100', 'Wages Payable', 'LIABILITY', true, now(), now()),
    (gen_random_uuid(), '3000', 'Owner''s Equity', 'EQUITY', true, now(), now()),
    (gen_random_uuid(), '4000', 'Service Revenue', 'REVENUE', true, now(), now()),
    (gen_random_uuid(), '5000', 'Salaries Expense', 'EXPENSE', true, now(), now()),
    (gen_random_uuid(), '5100', 'Rent Expense', 'EXPENSE', true, now(), now()),
    (gen_random_uuid(), '5200', 'Utilities Expense', 'EXPENSE', true, now(), now()),
    (gen_random_uuid(), '5300', 'Equipment Maintenance Expense', 'EXPENSE', true, now(), now()),
    (gen_random_uuid(), '5400', 'Bank Fees Expense', 'EXPENSE', true, now(), now());
