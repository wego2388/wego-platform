import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
            allWarningsAsErrors.set(true)
        }
    }
    jvmToolchain(25)

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
