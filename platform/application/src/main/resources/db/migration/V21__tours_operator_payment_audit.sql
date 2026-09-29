-- ============================================================
-- V21 — Tours Operator: append-only payment status history
-- ============================================================
--
-- tours_operator_payment keeps only the current state and overwrites status,
-- so a refund of a capture that first went to review loses the review step.
-- Every status change is now appended here in the same transaction as the
-- payment row itself.

CREATE TABLE wego.tours_operator_payment_audit_event (
    id              uuid        PRIMARY KEY,
    -- Insertion order. Two changes in one transaction share occurred_at
    -- (review then refund), so seq is the only reliable tie-breaker.
    seq             bigint      GENERATED ALWAYS AS IDENTITY,
    payment_id      uuid        NOT NULL
                        REFERENCES wego.tours_operator_payment (id) ON DELETE CASCADE,
    from_status     varchar(32),
    to_status       varchar(32) NOT NULL,
    provider_status varchar(32),
    occurred_at     timestamp with time zone NOT NULL,
    source          varchar(16) NOT NULL,

    CONSTRAINT tours_operator_payment_audit_status_known
        CHECK (
            to_status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED', 'REVIEW_REQUIRED', 'RECONCILIATION_REQUIRED') AND
            (from_status IS NULL OR from_status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED', 'REVIEW_REQUIRED', 'RECONCILIATION_REQUIRED'))
        ),
    CONSTRAINT tours_operator_payment_audit_source_known
        CHECK (source IN ('LIVE', 'BACKFILL'))
);

CREATE INDEX tours_operator_payment_audit_payment_idx
    ON wego.tours_operator_payment_audit_event (payment_id, occurred_at, seq);

-- Pre-V21 payments have no recorded history. Reconstruct only what the row
-- proves (creation and its current state) and mark it BACKFILL so staff can
-- tell reconstructed steps from recorded ones. Intermediate steps are unknown.
INSERT INTO wego.tours_operator_payment_audit_event
    (id, payment_id, from_status, to_status, provider_status, occurred_at, source)
SELECT gen_random_uuid(), p.id, NULL, 'PENDING', NULL, p.created_at, 'BACKFILL'
FROM wego.tours_operator_payment p;

INSERT INTO wego.tours_operator_payment_audit_event
    (id, payment_id, from_status, to_status, provider_status, occurred_at, source)
SELECT gen_random_uuid(), p.id, NULL, p.status, p.provider_status,
       COALESCE(p.refunded_at, p.failed_at, p.paid_at, p.created_at), 'BACKFILL'
FROM wego.tours_operator_payment p
WHERE p.status <> 'PENDING';

COMMENT ON TABLE wego.tours_operator_payment_audit_event IS
    'Append-only history of every payment status change. LIVE rows are written with the state change; BACKFILL rows are reconstructed from pre-V21 state and have no known from_status.';
