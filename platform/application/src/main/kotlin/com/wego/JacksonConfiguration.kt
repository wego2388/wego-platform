package com.wego

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature

/**
 * Applied by Boot's `JacksonAutoConfiguration` on top of its own defaults
 * when building the `@Primary JsonMapper` bean that Spring MVC uses for
 * every HTTP request/response body — confirmed by decompiling
 * `spring-boot-jackson-4.1.0.jar`'s `JacksonAutoConfiguration` that this
 * customizer interface (unlike a competing `ObjectMapper`/`JsonMapper`
 * bean) is exactly the supported extension point.
 *
 * Jackson 3's `JsonMapper` does not fail on an unknown JSON property by
 * default (verified with a real HTTP test, not assumed) — this closes
 * that gap app-wide so every `additionalProperties: false` schema in
 * `wego-api.yaml` is actually enforced at runtime, not just documented.
 */
@Configuration(proxyBeanMethods = false)
class JacksonConfiguration {
    @Bean
    fun failOnUnknownPropertiesCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
}
