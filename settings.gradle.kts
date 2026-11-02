pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        exclusiveContent {
            forRepository { maven("https://redirector.kotlinlang.org/maven/intellij-dependencies") }
            filter { includeModuleByRegex("org\\.jetbrains\\.kotlin", ".*-for-ide") }
        }
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases") {
            content {
                includeGroup("org.gradle")
            }
        }
    }
}

rootProject.name = "krosscope"

include(":core:model")
include(":core:spi")
include(":core:store")
include(":core:engine")
include(":analyzers:gradle-model-plugin")
include(":analyzers:gradle")
include(":analyzers:android")
include(":analyzers:compose")
include(":analyzers:history")
include(":analyzers:jni")
include(":analyzers:schema")
include(":analyzers:kotlin")
include(":analyzers:kotlin-worker")
include(":service")
include(":intelligence:retrieval")
include(":intelligence:context")
include(":cli")
include(":benchmark")
include(":tools:detekt-rules")
