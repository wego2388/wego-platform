-- Real pipeline confirmed by the plan: Lead -> Theory -> Pool -> Open
-- Water -> Certified, forward-only, with a separate terminal Withdrawn
-- reachable from any non-finished stage. This tracks a real diver's real
-- progress through a real course offering — no invented certification
-- taxonomy, just the stages every PADI-style course actually has.
CREATE TABLE wego.divers_course_enrollment (
    id uuid PRIMARY KEY,
    diver_id uuid NOT NULL REFERENCES wego.divers_diver (id) ON DELETE RESTRICT,
    offering_id uuid NOT NULL REFERENCES wego.divers_offering (id) ON DELETE RESTRICT,
    instructor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    stage varchar(16) NOT NULL DEFAULT 'LEAD',
    started_at timestamp with time zone NOT NULL,
    certified_at timestamp with time zone,
    withdrawn_at timestamp with time zone,
    created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT divers_course_enrollment_stage_known
        CHECK (stage IN ('LEAD', 'THEORY', 'POOL', 'OPEN_WATER', 'CERTIFIED', 'WITHDRAWN')),
    CONSTRAINT divers_course_enrollment_certified_at_matches_stage
        CHECK ((stage = 'CERTIFIED') = (certified_at IS NOT NULL)),
    CONSTRAINT divers_course_enrollment_withdrawn_at_matches_stage
        CHECK ((stage = 'WITHDRAWN') = (withdrawn_at IS NOT NULL))
);

CREATE INDEX divers_course_enrollment_diver_idx
    ON wego.divers_course_enrollment (diver_id);

CREATE INDEX divers_course_enrollment_offering_idx
    ON wego.divers_course_enrollment (offering_id);

CREATE INDEX divers_course_enrollment_active_idx
    ON wego.divers_course_enrollment (stage)
    WHERE stage NOT IN ('CERTIFIED', 'WITHDRAWN');

CREATE TABLE wego.divers_course_skill_evaluation (
    id uuid PRIMARY KEY,
    enrollment_id uuid NOT NULL REFERENCES wego.divers_course_enrollment (id) ON DELETE CASCADE,
    skill_name text NOT NULL,
    passed boolean NOT NULL,
    evaluated_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    evaluated_on date NOT NULL,
    notes text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT divers_course_skill_evaluation_skill_name_not_blank
        CHECK (length(trim(skill_name)) > 0)
);

CREATE INDEX divers_course_skill_evaluation_enrollment_idx
    ON wego.divers_course_skill_evaluation (enrollment_id, evaluated_on DESC);

CREATE TABLE wego.divers_course_enrollment_audit_event (
    id uuid PRIMARY KEY,
    enrollment_id uuid NOT NULL REFERENCES wego.divers_course_enrollment (id) ON DELETE CASCADE,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    from_stage varchar(16),
    to_stage varchar(16),
    correlation_id uuid,
    CONSTRAINT divers_course_enrollment_audit_event_type_known
        CHECK (event_type IN ('ENROLLMENT_CREATED', 'STAGE_ADVANCED', 'ENROLLMENT_WITHDRAWN'))
);

CREATE INDEX divers_course_enrollment_audit_event_enrollment_idx
    ON wego.divers_course_enrollment_audit_event (enrollment_id);

COMMENT ON TABLE wego.divers_course_enrollment IS
    'A real diver''s progress through a real COURSE offering: Lead -> Theory -> Pool -> Open Water -> Certified, or Withdrawn from any non-finished stage.';
COMMENT ON TABLE wego.divers_course_skill_evaluation IS
    'Append-only skill-assessment log, one row per real evaluation during a course.';
COMMENT ON TABLE wego.divers_course_enrollment_audit_event IS
    'Append-only record of enrollment lifecycle transitions.';

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'course:manage'),
    ('platform-admin', 'course:view');
