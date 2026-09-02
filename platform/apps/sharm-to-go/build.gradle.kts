import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.jooq.codegen)
    alias(libs.plugins.ktlint)
}

group = "com.wego"
version = rootProject.version

val generatedJooqDirectory = layout.buildDirectory.dir("generated-src/jooq/main")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceSets.named("main") {
        java.srcDir(generatedJooqDirectory)
    }
}

// This is the executable Sharm To Go application — WEGO-010-A Packet 0R.
// Unlike `:platform:application` (Sharm Divers Club), this module's source
// set adds ONLY `products/travel-marketplace`, never `products/divers`. That
// omission — not a runtime flag — is the actual isolation mechanism: this
// module's compiled jar cannot contain a Divers controller, bean, or route
// because that source is never on its compile classpath. See
// `clients/sharm-to-go/TECHNICAL_EXECUTION_PLAN.md`'s Packet 0R section.
kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
    sourceSets.named("main") {
        kotlin.srcDirs(
            "src/main/kotlin",
            "../../kernel/security/src/main/kotlin",
            "../../kernel/events/src/main/kotlin",
            "../../kernel/identity/src/main/kotlin",
            "../../../products/travel-marketplace/src/main/kotlin",
        )
    }
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.modulith.bom))

    implementation(kotlin("reflect"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(libs.spring.modulith.core)

    // Same bounded-cache rationale as `:platform:application` — see that
    // module's build script for the full note. `InMemoryLoginAttemptThrottle`
    // is kernel/identity code, compiled into this app the same way.
    implementation("com.github.ben-manes.caffeine:caffeine")

    runtimeOnly("org.postgresql:postgresql")

    jooqCodegen("org.jooq:jooq-meta-extensions:${libs.versions.jooq.get()}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.spring.modulith.test)
    testImplementation(libs.archunit.junit5)
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

jooq {
    configuration {
        logging = org.jooq.meta.jaxb.Logging.WARN
        generator {
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                inputSchema = "wego"
                properties {
                    property {
                        // Scoped to this module's own migration folder only
                        // (V1/V2 platform+identity foundation) — never the
                        // Divers app's V3+ files, which physically do not
                        // exist under this path.
                        key = "scripts"
                        value = file("src/main/resources/db/migration/*.sql").absolutePath
                    }
                    property {
                        key = "sort"
                        value = "flyway"
                    }
                    property {
                        key = "defaultNameCase"
                        value = "lower"
                    }
                }
            }
            generate {
                isDeprecated = false
                isRecords = true
                isRelations = true
                isFluentSetters = true
            }
            target {
                packageName = "com.wego.generated.jooq"
                directory = generatedJooqDirectory.get().asFile.absolutePath
            }
        }
    }
}

tasks.named("jooqCodegen") {
    val packageInfoFile = generatedJooqDirectory.get().file("com/wego/generated/package-info.java").asFile
    doLast {
        packageInfoFile.parentFile.mkdirs()
        packageInfoFile.writeText(
            """
            @org.springframework.modulith.ApplicationModule(
                displayName = "Generated Persistence Types",
                type = org.springframework.modulith.ApplicationModule.Type.OPEN
            )
            package com.wego.generated;

            """.trimIndent(),
        )
    }
}

tasks.named("compileJava") {
    dependsOn("jooqCodegen")
}

tasks.named("compileKotlin") {
    dependsOn("jooqCodegen")
}

tasks
    .matching {
        it.name == "runKtlintCheckOverMainSourceSet" ||
            it.name == "runKtlintFormatOverMainSourceSet"
    }.configureEach {
        dependsOn("jooqCodegen")
    }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}
