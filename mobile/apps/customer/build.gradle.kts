import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
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
            implementation(project(":mobile:shared"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
