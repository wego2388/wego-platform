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

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
    // This is the executable Sharm Divers Club application. It must never
    // add `products/travel-marketplace` (that is `:platform:apps:sharm-to-go`'s
    // job) — see WEGO-010-A Packet 0R. Before Packet 0R this line was
    // harmless because that product was an empty shell; once it gained real
    // domain/application/infrastructure/api code, leaving it here would have
    // recompiled it into this app too and reintroduced the exact composition
    // this packet exists to prevent.
    sourceSets.named("main") {
        kotlin.srcDirs(
            "src/main/kotlin",
            "../kernel/security/src/main/kotlin",
            "../kernel/events/src/main/kotlin",
            "../kernel/identity/src/main/kotlin",
            "../../products/divers/src/main/kotlin",
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

    // Bounded cache (hard maximumSize, amortized O(1) eviction) for
    // InMemoryLoginAttemptThrottle — a plain ConcurrentHashMap with manual
    // sweeping can't cap its own size under an attacker spraying many
    // distinct keys, and every request past the limit paid for a full
    // table scan on top of it. See that class's own doc comment for why
    // its cap is sized against achievable single-source spray volume
    // rather than against Caffeine's frequency-aware eviction protecting a
    // specific hot key — that was checked empirically and did not hold for
    // this write-heavy access pattern. Version managed by the Spring Boot
    // BOM above.
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

// jOOQ's DDLDatabase generator only emits table/record classes; it has no
// concept of a Spring Modulith package-info.java, and the whole directory is
// regenerated on every run. Application code across every module needs to use
// these generated types as ordinary infrastructure, not as a bounded business
// module, so this marks the top-level generated package OPEN on each
// generation instead of hand-maintaining a file that would be wiped.
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
    // Ryuk (Testcontainers' resource-reaper sidecar) is pulled from
    // docker.io on every fresh environment and has no pinned-mirror fallback
    // the way the application's own base images do (see
    // infrastructure/compose/compose.yaml) — a registry outage or block
    // silently degrades `@Testcontainers(disabledWithoutDocker = true)`
    // tests to skipped instead of failing loud. Ryuk's only job is cleaning
    // up containers a crashed test run left behind; a CI runner is destroyed
    // after the job regardless, so it has nothing to do there, and locally a
    // developer can `docker compose down`/`docker system prune` by hand.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}
