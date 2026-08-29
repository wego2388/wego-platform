package com.wego.divers.domain

import java.util.UUID

@JvmInline
value class CourseEnrollmentId(
    val value: UUID,
) {
    companion object {
        fun generate(): CourseEnrollmentId = CourseEnrollmentId(UUID.randomUUID())
    }
}
