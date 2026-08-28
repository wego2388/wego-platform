package com.wego.mobile.shared.offline

import kotlinx.coroutines.flow.StateFlow

private val stableIdentifierPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$")
private val commandTypePattern = Regex("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$")

// Plain data classes, not @JvmInline value classes: @JvmInline is JVM-only and
// Kotlin/Native (iOS) has no equivalent, so a truly shared commonMain type
// cannot use it. The cost is one extra allocation per ID on JVM/Android — not
// a hot path here.
@ConsistentCopyVisibility
data class CommandId private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): CommandId = CommandId(validateStableIdentifier("commandId", value))
    }
}

@ConsistentCopyVisibility
data class IdempotencyKey private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): IdempotencyKey = IdempotencyKey(validateStableIdentifier("idempotencyKey", value))
    }
}

@ConsistentCopyVisibility
data class ActorId private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): ActorId = ActorId(validateStableIdentifier("actorId", value))
    }
}

@ConsistentCopyVisibility
data class DeviceId private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): DeviceId = DeviceId(validateStableIdentifier("deviceId", value))
    }
}

@ConsistentCopyVisibility
data class CommandType private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): CommandType {
            require(commandTypePattern.matches(value)) {
                "commandType must be a lowercase, namespaced identifier"
            }
            return CommandType(value)
        }
    }
}

data class CommandOrigin(
    val actorId: ActorId,
    val deviceId: DeviceId,
)

interface OfflineCommandPayload

data class OfflineCommandEnvelope<out Payload : OfflineCommandPayload>(
    val commandId: CommandId,
    val idempotencyKey: IdempotencyKey,
    val commandType: CommandType,
    val commandVersion: Int,
    val createdAtEpochMilliseconds: Long,
    val origin: CommandOrigin,
    val dependsOn: List<CommandId> = emptyList(),
    val payload: Payload,
) {
    init {
        require(commandVersion > 0) { "commandVersion must be positive" }
        require(createdAtEpochMilliseconds >= 0) { "createdAtEpochMilliseconds must be non-negative" }
        require(commandId !in dependsOn) { "A command cannot depend on itself" }
        require(dependsOn.distinct().size == dependsOn.size) { "Command dependencies must be unique" }
    }
}

enum class EnqueueResult {
    QUEUED,
    ALREADY_QUEUED,
}

interface OfflineCommandQueue {
    val pendingCommands: StateFlow<List<OfflineCommandEnvelope<OfflineCommandPayload>>>

    suspend fun enqueue(command: OfflineCommandEnvelope<OfflineCommandPayload>): EnqueueResult

    suspend fun removeAcknowledged(commandId: CommandId)
}

private fun validateStableIdentifier(
    field: String,
    value: String,
): String {
    require(stableIdentifierPattern.matches(value)) {
        "$field must contain 8-128 stable identifier characters"
    }
    return value
}
