pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wego-platform"

include(":platform:application")
include(":platform:apps:sharm-to-go")
include(":mobile:shared")
include(":mobile:apps:ops")
include(":mobile:apps:customer")
include(":mobile:apps:customer-android")

project(":platform:application").projectDir = file("platform/application")
project(":platform:apps:sharm-to-go").projectDir = file("platform/apps/sharm-to-go")
