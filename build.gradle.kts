plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
}

group = "com.wego"
version = "0.1.0-SNAPSHOT"

tasks.named("check") {
    dependsOn(":platform:application:check")
    dependsOn(":mobile:shared:check")
    dependsOn(":mobile:apps:ops:check")
    dependsOn(":mobile:apps:customer:check")
    dependsOn(":mobile:apps:customer-android:check")
}

tasks.named("assemble") {
    dependsOn(":platform:application:assemble")
}
