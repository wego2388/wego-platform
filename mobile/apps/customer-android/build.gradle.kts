import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.wego.mobile.customer.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wego.mobile.customer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":mobile:apps:customer"))
    implementation(libs.androidx.activity.compose)
}
