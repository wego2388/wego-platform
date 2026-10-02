package com.wego

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.modulith.Modulithic
import org.springframework.scheduling.annotation.EnableScheduling

/** [EnableScheduling] is required for [com.wego.travelmarketplace.infrastructure.ExpireTravelRequestsScheduler]'s `@Scheduled` method to actually run. */
@Modulithic(systemName = "Wego Sharm To Go")
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@EnableScheduling
class WegoApplication

fun main(args: Array<String>) {
    runApplication<WegoApplication>(*args)
}
