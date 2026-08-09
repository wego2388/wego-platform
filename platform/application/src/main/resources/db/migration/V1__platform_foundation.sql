CREATE SCHEMA wego;

CREATE TYPE wego.outbox_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'PUBLISHED',
    'FAILED'
);

CREATE TABLE wego.integration_outbox (
    id uuid PRIMARY KEY,
    aggregate_type varchar(100) NOT NULL,
    aggregate_id varchar(100) NOT NULL,
    event_type varchar(200) NOT NULL,
    event_version integer NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    available_at timestamp with time zone NOT NULL,
    status wego.outbox_status NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    locked_by varchar(100),
    locked_at timestamp with time zone,
    last_error text,
    published_at timestamp with time zone,
    correlation_id uuid,
    causation_id uuid,
    CONSTRAINT integration_outbox_aggregate_type_not_blank
        CHECK (length(trim(aggregate_type)) > 0),
    CONSTRAINT integration_outbox_aggregate_id_not_blank
        CHECK (length(trim(aggregate_id)) > 0),
    CONSTRAINT integration_outbox_event_type_not_blank
        CHECK (length(trim(event_type)) > 0),
    CONSTRAINT integration_outbox_event_version_positive
        CHECK (event_version > 0),
    CONSTRAINT integration_outbox_attempt_count_nonnegative
        CHECK (attempt_count >= 0),
    CONSTRAINT integration_outbox_lock_pair
        CHECK ((locked_by IS NULL) = (locked_at IS NULL)),
    CONSTRAINT integration_outbox_published_state
        CHECK (
            (status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR (status <> 'PUBLISHED' AND published_at IS NULL)
        )
);

CREATE INDEX integration_outbox_pending_idx
    ON wego.integration_outbox (available_at, occurred_at, id)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX integration_outbox_processing_idx
    ON wego.integration_outbox (locked_at)
    WHERE status = 'PROCESSING';

COMMENT ON TABLE wego.integration_outbox IS
    'Durable integration events written in the originating business transaction.';
