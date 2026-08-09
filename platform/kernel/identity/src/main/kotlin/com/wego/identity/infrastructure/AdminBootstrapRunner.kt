package com.wego.identity.infrastructure

import com.wego.identity.application.AdminBootstrapService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.Console

/**
 * Operator-run, one-shot bootstrap of the first platform user. Only active
 * under the `bootstrap-admin` profile so it can never fire during ordinary
 * server startup; [AdminBootstrapService] separately refuses to run once any
 * user already exists. Credentials are read from an interactive console
 * (never from a command-line argument, environment variable dump, or log)
 * so the password never lands in shell history or process listings.
 *
 * Usage: `java -jar wego.jar --spring.profiles.active=bootstrap-admin`
 */
@Component
@Profile("bootstrap-admin")
class AdminBootstrapRunner(
    private val adminBootstrapService: AdminBootstrapService,
    private val context: ConfigurableApplicationContext,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val console = System.console()
        val exitCode =
            if (console == null) {
                System.err.println("No interactive console is attached; bootstrap-admin requires a TTY.")
                1
            } else {
                runBootstrap(console)
            }
        SpringApplication.exit(context, { exitCode })
    }

    private fun runBootstrap(console: Console): Int {
        val email = console.readLine("Admin email: ")?.trim().orEmpty()
        val password = String(console.readPassword("Admin password: "))
        val confirmation = String(console.readPassword("Confirm password: "))

        if (password != confirmation) {
            console.printf("Passwords did not match. Nothing was created.%n")
            return 1
        }

        return runCatching { adminBootstrapService.bootstrap(email, password) }
            .fold(
                onSuccess = { user ->
                    console.printf("Created platform admin %s (id=%s).%n", user.email.value, user.id.value)
                    0
                },
                onFailure = { error ->
                    console.printf("Bootstrap failed: %s%n", error.message)
                    1
                },
            )
    }
}
