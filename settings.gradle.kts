pluginManagement {
    repositories {
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
include(":mobile:shared")
include(":mobile:apps:ops")
include(":mobile:apps:customer")

project(":platform:application").projectDir = file("platform/application")
