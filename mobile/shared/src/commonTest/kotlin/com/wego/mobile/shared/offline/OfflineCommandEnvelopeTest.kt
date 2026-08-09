package com.wego.mobile.shared.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfflineCommandEnvelopeTest {
    @Test
    fun `retry preserves stable command and idempotency identity`() {
        val command = command()
        val retry = command.copy()

        assertEquals(command.commandId, retry.commandId)
        assertEquals(command.idempotencyKey, retry.idempotencyKey)
        assertEquals(command, retry)
    }

    @Test
    fun `command cannot depend on itself`() {
        val commandId = CommandId.of("command:00000001")

        assertFailsWith<IllegalArgumentException> {
            command(commandId = commandId, dependsOn = listOf(commandId))
        }
    }

    @Test
    fun `command type must be namespaced`() {
        assertFailsWith<IllegalArgumentException> {
            CommandType.of("refund")
        }
    }

    private fun command(
        commandId: CommandId = CommandId.of("command:00000001"),
        dependsOn: List<CommandId> = emptyList(),
    ): OfflineCommandEnvelope<TestPayload> =
        OfflineCommandEnvelope(
            commandId = commandId,
            idempotencyKey = IdempotencyKey.of("idempotency:00000001"),
            commandType = CommandType.of("booking.create"),
            commandVersion = 1,
            createdAtEpochMilliseconds = 1_786_232_400_000,
            origin =
                CommandOrigin(
                    actorId = ActorId.of("actor:00000001"),
                    deviceId = DeviceId.of("device:00000001"),
                ),
            dependsOn = dependsOn,
            payload = TestPayload(reference = "local:00000001"),
        )

    private data class TestPayload(
        val reference: String,
    ) : OfflineCommandPayload
}
