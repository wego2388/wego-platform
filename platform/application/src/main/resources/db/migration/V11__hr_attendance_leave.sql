CREATE TABLE wego.hr_attendance_record (
    id uuid PRIMARY KEY,
    employee_id uuid NOT NULL REFERENCES wego.hr_employee (id) ON DELETE CASCADE,
    attendance_date date NOT NULL,
    status varchar(16) NOT NULL,
    clock_in timestamp with time zone,
    clock_out timestamp with time zone,
    notes text,
    created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT hr_attendance_record_status_known
        CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'HALF_DAY')),
    CONSTRAINT hr_attendance_record_clock_out_after_clock_in
        CHECK (clock_in IS NULL OR clock_out IS NULL OR clock_out >= clock_in),
    -- One real record per employee per day — recording attendance again for
    -- the same day is a correction (upsert), not a second, conflicting row.
    CONSTRAINT hr_attendance_record_employee_date_unique
        UNIQUE (employee_id, attendance_date)
);

CREATE INDEX hr_attendance_record_employee_idx
    ON wego.hr_attendance_record (employee_id);

CREATE INDEX hr_attendance_record_date_idx
    ON wego.hr_attendance_record (attendance_date);

CREATE TABLE wego.hr_leave_request (
    id uuid PRIMARY KEY,
    employee_id uuid NOT NULL REFERENCES wego.hr_employee (id) ON DELETE CASCADE,
    leave_type varchar(16) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    reason text,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    requested_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    requested_at timestamp with time zone NOT NULL,
    decided_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    decided_at timestamp with time zone,
    decision_notes text,
    cancelled_at timestamp with time zone,
    CONSTRAINT hr_leave_request_type_known
        CHECK (leave_type IN ('ANNUAL', 'SICK', 'UNPAID', 'OTHER')),
    CONSTRAINT hr_leave_request_status_known
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT hr_leave_request_end_not_before_start
        CHECK (end_date >= start_date),
    -- Each status has exactly one matching set of "when did this happen"
    -- fields — a decision (approve/reject) is never confused with a plain
    -- withdrawal (cancel), and a still-open request carries neither.
    CONSTRAINT hr_leave_request_lifecycle_fields_match_status
        CHECK (
            (status IN ('APPROVED', 'REJECTED') AND decided_at IS NOT NULL AND decided_by_user_id IS NOT NULL AND cancelled_at IS NULL)
            OR (status = 'PENDING' AND decided_at IS NULL AND cancelled_at IS NULL)
            OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND decided_at IS NULL)
        )
);

CREATE INDEX hr_leave_request_employee_idx
    ON wego.hr_leave_request (employee_id);

CREATE INDEX hr_leave_request_status_idx
    ON wego.hr_leave_request (status);

CREATE TABLE wego.hr_leave_request_audit_event (
    id uuid PRIMARY KEY,
    leave_request_id uuid NOT NULL REFERENCES wego.hr_leave_request (id) ON DELETE CASCADE,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    reason text,
    correlation_id uuid,
    CONSTRAINT hr_leave_request_audit_event_type_known
        CHECK (event_type IN ('LEAVE_REQUESTED', 'LEAVE_APPROVED', 'LEAVE_REJECTED', 'LEAVE_CANCELLED'))
);

CREATE INDEX hr_leave_request_audit_event_leave_idx
    ON wego.hr_leave_request_audit_event (leave_request_id);

COMMENT ON TABLE wego.hr_attendance_record IS
    'One real attendance record per employee per calendar day; recording again for the same day corrects it rather than adding a second row.';
COMMENT ON TABLE wego.hr_leave_request IS
    'A real leave-request approval workflow: PENDING -> APPROVED/REJECTED (a decision) or PENDING -> CANCELLED (a withdrawal, never a decision).';
COMMENT ON TABLE wego.hr_leave_request_audit_event IS
    'Append-only record of leave-request lifecycle mutations, same shape as hr_employee_audit_event.';

-- New attendance/leave permissions, following V9/V10's catalog discipline.
INSERT INTO wego.identity_permission (code, description) VALUES
    ('hr:attendance-view', 'View employee attendance records.'),
    ('hr:attendance-manage', 'Record and correct employee attendance.'),
    ('hr:leave-view', 'View employee leave requests.'),
    ('hr:leave-manage', 'Submit, approve, reject, and cancel employee leave requests.');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'hr:attendance-view'),
    ('platform-admin', 'hr:attendance-manage'),
    ('platform-admin', 'hr:leave-view'),
    ('platform-admin', 'hr:leave-manage'),
    ('hr-manager', 'hr:attendance-view'),
    ('hr-manager', 'hr:attendance-manage'),
    ('hr-manager', 'hr:leave-view'),
    ('hr-manager', 'hr:leave-manage'),
    ('operations-manager', 'hr:attendance-view'),
    ('operations-manager', 'hr:attendance-manage'),
    ('operations-manager', 'hr:leave-view'),
    ('operations-manager', 'hr:leave-manage');
