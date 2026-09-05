package com.wego.architecture

import com.wego.WegoApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModuleArchitectureTest {
    @Test
    fun `application modules obey declared boundaries`() {
        ApplicationModules.of(WegoApplication::class.java).verify()
    }
}
