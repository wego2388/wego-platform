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
val sharedMigrationDirectory = rootProject.file("platform/application/src/main/resources/db/migration")

val selectedSharedDdlMigrations =
    listOf(
        sharedMigrationDirectory.resolve("V1__platform_foundation.sql"),
        sharedMigrationDirectory.resolve("V2__identity_foundation.sql"),
        sharedMigrationDirectory.resolve("V14__tours_operator_foundation.sql"),
        sharedMigrationDirectory.resolve("V16__tours_operator_catalog_content.sql"),
        sharedMigrationDirectory.resolve("V18__tours_operator_payment.sql"),
        sharedMigrationDirectory.resolve("V19__tours_operator_payment_hardening.sql"),
        sharedMigrationDirectory.resolve("V20__tours_operator_payment_revenue_recognition.sql"),
        sharedMigrationDirectory.resolve("V21__tours_operator_payment_audit.sql"),
        sharedMigrationDirectory.resolve("V22__tours_operator_notification.sql"),
    )
val selectedDdlMigrations =
    selectedSharedDdlMigrations + file("src/main/resources/db/migration/V3__identity_administration.sql")

val generatedMigrationResources = layout.buildDirectory.dir("generated-resources/main")
val generatedJooqMigrations = layout.buildDirectory.dir("generated-migrations/jooq")
val stageSelectedMigrations by tasks.registering(Sync::class) {
    into(generatedMigrationResources)
    // V3 is already owned by this app's normal resource directory. Only stage
    // selected shared migrations so processResources never sees a duplicate.
    from(selectedSharedDdlMigrations) {
        into("db/migration")
    }
    from(sharedMigrationDirectory.resolve("data/V17__tours_operator_catalog_seed.sql")) {
        into("db/migration/data")
    }
}
val stageJooqMigrations by tasks.registering(Sync::class) {
    into(generatedJooqMigrations)
    from(selectedDdlMigrations)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceSets.named("main") {
        java.srcDir(generatedJooqDirectory)
        resources.srcDir(generatedMigrationResources)
    }
}

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
            "../../kernel/transaction/src/main/kotlin",
            "../../../products/tours-operator/src/main/kotlin",
        )
    }
    sourceSets.named("test") {
        // Product tests stay source-owned in the existing application module
        // during this extraction, but compile and run against this artifact's
        // selected classes and migrations as well.
        kotlin.srcDir("../../application/src/test/kotlin/com/wego/toursoperator")
        // Pure domain tests owned by the product itself. Without this they were
        // compiled by no module after the WEGO-017 extraction and never ran.
        kotlin.srcDir("../../../products/tours-operator/src/test/kotlin")
    }
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.modulith.bom))

    implementation(kotlin("reflect"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(libs.spring.modulith.core)
    implementation("com.github.ben-manes.caffeine:caffeine")

    runtimeOnly("org.postgresql:postgresql")

    jooqCodegen("org.jooq:jooq-meta-extensions:${libs.versions.jooq.get()}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.spring.modulith.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.greenmail.junit5)
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

tasks.named("processResources") {
    dependsOn(stageSelectedMigrations)
}

tasks.named("jooqCodegen") {
    dependsOn(stageJooqMigrations)
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
    jvmArgs("-Xmx1536m")
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}
