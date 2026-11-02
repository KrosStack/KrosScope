pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "classes"

include(":app", ":lib", ":ui", ":feature", ":core", ":kmp", ":plain", ":dynamic", ":flavored", ":scripted", ":aggregate")
