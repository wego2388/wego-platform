package com.wego.events

import java.util.UUID

/**
 * The current request's correlation id, made available to any module
 * without per-controller header parsing. A `Filter` (see
 * `com.wego.identity.infrastructure.CorrelationIdFilter` — HTTP/Servlet
 * wiring for the whole request pipeline is centralized in the identity
 * module already, alongside the other filters in the same chain; this
 * plain holder is what lets other modules read the value without
 * depending on that internal package) sets this once per request to
 * either a valid incoming `X-Correlation-Id` or a freshly generated UUID,
 * and clears it when the request completes. Plain JDK, no Spring/Servlet
 * dependency, which is what makes it safe to place at this module's root.
 */
object CorrelationContext {
    private val holder = ThreadLocal<UUID>()

    fun currentCorrelationId(): UUID? = holder.get()

    fun set(correlationId: UUID) {
        holder.set(correlationId)
    }

    fun clear() {
        holder.remove()
    }
}
