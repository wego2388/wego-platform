import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            allWarningsAsErrors.set(true)
        }
    }
    android {
        namespace = "com.wego.mobile.customer"
        compileSdk = 36
        minSdk = 26

        withHostTestBuilder {}.configure {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()
    jvmToolchain(25)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mobile:shared"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.navigation.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
