package com.wego.toursoperator.application

import com.wego.toursoperator.domain.SalesControl

interface SalesControlRepository {
    /** The stored switch, or [SalesControl.OPEN] when none was ever set. */
    fun current(): SalesControl

    fun save(control: SalesControl)
}
