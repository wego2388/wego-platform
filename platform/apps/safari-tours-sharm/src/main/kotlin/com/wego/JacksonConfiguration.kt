package com.wego

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature

@Configuration(proxyBeanMethods = false)
class JacksonConfiguration {
    @Bean
    fun failOnUnknownPropertiesCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
}
