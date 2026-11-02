pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tasktide"

include(":app")
include(":desktop")
include(":core:model")
include(":core:legacy")
include(":data:db")
include(":data:settings")
include(":sync:protocol")
include(":sync:engine")
include(":sync:service")
include(":feature:tasks")
include(":feature:settings")
include(":ui:shared")
include(":native:bridge")
