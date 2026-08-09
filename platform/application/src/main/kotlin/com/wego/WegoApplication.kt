package com.wego

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.modulith.Modulithic

@Modulithic(systemName = "Wego Platform")
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class WegoApplication

fun main(args: Array<String>) {
    runApplication<WegoApplication>(*args)
}
