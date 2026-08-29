package com.wego.divers

import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.CourseSkillEvaluation
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.EnrollmentStage
import com.wego.divers.domain.OfferingId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CourseSkillEvaluationTest {
    @Test
    fun `rejects a blank skill name`() {
        assertThatIllegalArgumentException().isThrownBy {
            CourseSkillEvaluation(UUID.randomUUID(), CourseEnrollmentId.generate(), "  ", true, null, LocalDate.now(), null, Instant.now())
        }
    }
}

class CourseEnrollmentTest {
    private fun create(): CourseEnrollment =
        CourseEnrollment.create(
            id = CourseEnrollmentId.generate(),
            diverId = DiverId.generate(),
            offeringId = OfferingId.generate(),
            createdByUserId = null,
            now = Instant.parse("2026-08-29T00:00:00Z"),
        )

    @Test
    fun `starts at LEAD with no certified or withdrawn timestamp`() {
        val enrollment = create()
        assertThat(enrollment.stage).isEqualTo(EnrollmentStage.LEAD)
        assertThat(enrollment.isFinished).isFalse()
        assertThat(enrollment.certifiedAt).isNull()
        assertThat(enrollment.withdrawnAt).isNull()
    }

    @Test
    fun `advances one real stage at a time through the whole pipeline`() {
        val enrollment = create()
        val now = Instant.parse("2026-09-01T00:00:00Z")

        enrollment.advance(now)
        assertThat(enrollment.stage).isEqualTo(EnrollmentStage.THEORY)

        enrollment.advance(now)
        assertThat(enrollment.stage).isEqualTo(EnrollmentStage.POOL)

        enrollment.advance(now)
        assertThat(enrollment.stage).isEqualTo(EnrollmentStage.OPEN_WATER)

        enrollment.advance(now)
        assertThat(enrollment.stage).isEqualTo(EnrollmentStage.CERTIFIED)
        assertThat(enrollment.isFinished).isTrue()
        assertThat(enrollment.certifiedAt).isEqualTo(now)
    }

    @Test
    fun `cannot advance a certified enrollment further`() {
        val enrollment = create()
        val now = Instant.parse("2026-09-01T00:00:00Z")
        repeat(4) { enrollment.advance(now) }

        assertThatIllegalArgumentException().isThrownBy { enrollment.advance(now) }
    }

    @Test
    fun `withdrawing sets stage and timestamp together, and is terminal`() {
        val enrollment = create()
        enrollment.advance(Instant.parse("2026-09-01T00:00:00Z"))
        val withdrawnAt = Instant.parse("2026-09-05T00:00:00Z")

        enrollment.withdraw(withdrawnAt)

        assertThat(enrollment.stage).isEqualTo(EnrollmentStage.WITHDRAWN)
        assertThat(enrollment.isFinished).isTrue()
        assertThat(enrollment.withdrawnAt).isEqualTo(withdrawnAt)
        assertThatIllegalArgumentException().isThrownBy { enrollment.withdraw(withdrawnAt) }
        assertThatIllegalArgumentException().isThrownBy { enrollment.advance(withdrawnAt) }
    }

    @Test
    fun `cannot assign an instructor to a finished enrollment`() {
        val enrollment = create()
        enrollment.withdraw(Instant.parse("2026-09-01T00:00:00Z"))

        assertThatIllegalArgumentException().isThrownBy { enrollment.assignInstructor(UUID.randomUUID()) }
    }

    @Test
    fun `assigns an instructor to an in-progress enrollment`() {
        val enrollment = create()
        val instructorId = UUID.randomUUID()

        enrollment.assignInstructor(instructorId)

        assertThat(enrollment.instructorUserId).isEqualTo(instructorId)
    }
}
