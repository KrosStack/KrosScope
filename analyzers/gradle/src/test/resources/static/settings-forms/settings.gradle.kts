pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "forms"

include(":app")
include(":core:model", ":core:legacy")
include("feature:tasks", "feature:settings")
project(":core:legacy").projectDir = file("legacy-impl")
includeBuild("tools")
