package com.wego.hr

import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.LeaveRequest
import com.wego.hr.domain.LeaveRequestId
import com.wego.hr.domain.LeaveRequestStatus
import com.wego.hr.domain.LeaveType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class LeaveRequestTest {
    private val now = Instant.parse("2026-08-30T00:00:00Z")

    private fun submit(
        startDate: LocalDate = LocalDate.parse("2026-09-01"),
        endDate: LocalDate = LocalDate.parse("2026-09-05"),
    ): LeaveRequest =
        LeaveRequest.submit(
            id = LeaveRequestId.generate(),
            employeeId = EmployeeId.generate(),
            leaveType = LeaveType.ANNUAL,
            startDate = startDate,
            endDate = endDate,
            reason = "Family trip",
            requestedByUserId = null,
            now = now,
        )

    @Test
    fun `rejects an end date before the start date`() {
        assertThatIllegalArgumentException().isThrownBy {
            submit(startDate = LocalDate.parse("2026-09-05"), endDate = LocalDate.parse("2026-09-01"))
        }
    }

    @Test
    fun `starts pending with no decision or cancellation`() {
        val request = submit()
        assertThat(request.status).isEqualTo(LeaveRequestStatus.PENDING)
        assertThat(request.isPending).isTrue()
        assertThat(request.decidedAt).isNull()
        assertThat(request.cancelledAt).isNull()
    }

    @Test
    fun `approving sets status and decision fields together`() {
        val request = submit()
        val actor = UUID.randomUUID()
        val decidedAt = Instant.parse("2026-08-31T00:00:00Z")

        val approved = request.approve(actor, decidedAt, "Enjoy")

        assertThat(approved.status).isEqualTo(LeaveRequestStatus.APPROVED)
        assertThat(approved.decidedByUserId).isEqualTo(actor)
        assertThat(approved.decidedAt).isEqualTo(decidedAt)
        assertThat(approved.decisionNotes).isEqualTo("Enjoy")
        assertThat(approved.cancelledAt).isNull()
    }

    @Test
    fun `rejecting sets status and decision fields together`() {
        val request = submit()
        val rejected = request.reject(UUID.randomUUID(), Instant.parse("2026-08-31T00:00:00Z"), "Understaffed that week")

        assertThat(rejected.status).isEqualTo(LeaveRequestStatus.REJECTED)
        assertThat(rejected.decidedAt).isNotNull()
        assertThat(rejected.cancelledAt).isNull()
    }

    @Test
    fun `cancelling sets cancelledAt and never a decision`() {
        val request = submit()
        val cancelledAt = Instant.parse("2026-08-31T00:00:00Z")

        val cancelled = request.cancel(cancelledAt)

        assertThat(cancelled.status).isEqualTo(LeaveRequestStatus.CANCELLED)
        assertThat(cancelled.cancelledAt).isEqualTo(cancelledAt)
        assertThat(cancelled.decidedAt).isNull()
        assertThat(cancelled.decidedByUserId).isNull()
    }

    @Test
    fun `an already-approved request cannot be approved, rejected, or cancelled again`() {
        val approved = submit().approve(UUID.randomUUID(), Instant.parse("2026-08-31T00:00:00Z"), null)

        assertThatIllegalArgumentException().isThrownBy { approved.approve(UUID.randomUUID(), now, null) }
        assertThatIllegalArgumentException().isThrownBy { approved.reject(UUID.randomUUID(), now, null) }
        assertThatIllegalArgumentException().isThrownBy { approved.cancel(now) }
    }

    @Test
    fun `overlaps detects an actual date-range intersection, not just an equal range`() {
        val a = submit(startDate = LocalDate.parse("2026-09-01"), endDate = LocalDate.parse("2026-09-10"))
        val overlapping = submit(startDate = LocalDate.parse("2026-09-05"), endDate = LocalDate.parse("2026-09-15"))
        val adjacentNotOverlapping = submit(startDate = LocalDate.parse("2026-09-11"), endDate = LocalDate.parse("2026-09-20"))

        assertThat(a.overlaps(overlapping)).isTrue()
        assertThat(a.overlaps(adjacentNotOverlapping)).isFalse()
    }
}
