pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TaskTide"

include(":app")
include(":core:model", ":core:legacy")
include(
    ":feature:tasks",
    ":feature:settings",
)
project(":sync:engine").projectDir = file("sync/engine-impl")

if (providers.gradleProperty("withNative").isPresent) {
    include(":native:bridge")
}

val extras = listOf("a", "b")
extras.forEach { include(":extras:$it") }

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
