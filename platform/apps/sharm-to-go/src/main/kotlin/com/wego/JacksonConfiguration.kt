package com.wego

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature

/**
 * Identical to `:platform:application`'s own `JacksonConfiguration` — see
 * that module's copy for the full rationale. Kept as a real duplicate file
 * rather than a shared dependency because this app and the Divers app are
 * deliberately never on the same compile classpath (that separation is the
 * whole point of Packet 0R); a shared "app-shell" module would need its own
 * isolation review before either app could safely depend on it.
 */
@Configuration(proxyBeanMethods = false)
class JacksonConfiguration {
    @Bean
    fun failOnUnknownPropertiesCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
}
