-- Remediation for a real duplicate-enrollment bug found by independent
-- Tier 1 review: repeating the same enroll request created two distinct
-- LEAD rows for the same diver/course pair, and nothing stopped a diver
-- from having two simultaneously active enrollments in the same course.
-- WITHDRAWN is excluded so a diver can be re-enrolled after withdrawing;
-- CERTIFIED stays covered so a completed enrollment isn't duplicated.
CREATE UNIQUE INDEX divers_course_enrollment_one_active_per_diver_offering
    ON wego.divers_course_enrollment (diver_id, offering_id)
    WHERE stage != 'WITHDRAWN';
