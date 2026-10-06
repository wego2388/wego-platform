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
val generatedJooqMigrations = layout.buildDirectory.dir("generated-migrations/jooq")
val diversReleaseMigrations =
    listOf(
        "V1__platform_foundation.sql",
        "V2__identity_foundation.sql",
        "V3__divers_booking_foundation.sql",
        "V4__divers_diver_profiles.sql",
        "V5__divers_equipment_tracking.sql",
        "V6__divers_boat_charter.sql",
        "V7__divers_course_enrollment.sql",
        "V8__divers_course_enrollment_uniqueness.sql",
        "V9__identity_administration.sql",
        "V10__hr_foundation.sql",
        "V11__hr_attendance_leave.sql",
        "V12__accounting_foundation.sql",
        "V13__payroll_foundation.sql",
    )
val nonDiversMigrationResources =
    listOf(
        "db/migration/V14__tours_operator_foundation.sql",
        "db/migration/V15__travel_marketplace_catalog.sql",
        "db/migration/V16__tours_operator_catalog_content.sql",
        "db/migration/data/V17__tours_operator_catalog_seed.sql",
        "db/migration/V18__tours_operator_payment.sql",
        "db/migration/V19__tours_operator_payment_hardening.sql",
        "db/migration/V20__tours_operator_payment_revenue_recognition.sql",
        "db/migration/V21__tours_operator_payment_audit.sql",
        "db/migration/V22__tours_operator_notification.sql",
        "db/migration/V23__tours_operator_tour_content.sql",
        "db/migration/V24__tours_operator_unit_pricing.sql",
        "db/migration/data/V25__tours_operator_catalog_revision_and_seats.sql",
        "db/migration/V26__tours_operator_sales_control.sql",
        "db/migration/V27__tours_operator_captured_payment_review.sql",
        "db/migration/V28__tours_operator_payment_refund_callback.sql",
        "db/migration/V29__tours_operator_asset_registry.sql",
        "db/migration/V30__tours_operator_office_booking.sql",
        "db/migration/V31__tours_operator_office_documents.sql",
    )
val stageDiversJooqMigrations by tasks.registering(Sync::class) {
    into(generatedJooqMigrations)
    from(diversReleaseMigrations.map { file("src/main/resources/db/migration/$it") })
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceSets.named("main") {
        java.srcDir(generatedJooqDirectory)
        resources.exclude(nonDiversMigrationResources)
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
            "../kernel/transaction/src/main/kotlin",
            "../../products/divers/src/main/kotlin",
            "../../products/hr/src/main/kotlin",
            "../../products/accounting/src/main/kotlin",
            "../../products/payroll/src/main/kotlin",
        )
    }
    sourceSets.named("test") {
        kotlin.exclude("com/wego/toursoperator/**")
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
                        // DML-only seed files live in db/migration/data/ and are intentionally
                        // excluded from jOOQ codegen — H2/DDLDatabase cannot parse ON CONFLICT.
                        value =
                            generatedJooqMigrations
                                .get()
                                .asFile
                                .resolve("*.sql")
                                .absolutePath
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
    dependsOn(stageDiversJooqMigrations)
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
    // The suite now covers more products (travel-marketplace, tours-operator,
    // hr, accounting, payroll) each spinning their own Testcontainers
    // PostgreSQL instance — raise the heap from the default 512 m.
    jvmArgs("-Xmx1536m")
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}
