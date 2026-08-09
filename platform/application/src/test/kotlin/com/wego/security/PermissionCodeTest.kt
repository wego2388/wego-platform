package com.wego.security

import com.wego.security.PermissionCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class PermissionCodeTest {
    @Test
    fun `accepts explicit resource action codes`() {
        assertThat(PermissionCode.of("booking:cancel").value).isEqualTo("booking:cancel")
    }

    @Test
    fun `rejects ambiguous permission names`() {
        assertThatIllegalArgumentException()
            .isThrownBy { PermissionCode.of("ADMIN") }
    }
}
