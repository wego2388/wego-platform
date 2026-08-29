package com.wego.divers.domain

enum class EnrollmentStage {
    LEAD,
    THEORY,
    POOL,
    OPEN_WATER,
    CERTIFIED,
    WITHDRAWN,
    ;

    companion object {
        /** The real forward pipeline — Withdrawn is reachable from any of these but is not itself a step in it. */
        val PROGRESSION = listOf(LEAD, THEORY, POOL, OPEN_WATER, CERTIFIED)
    }
}
