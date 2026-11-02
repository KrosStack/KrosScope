plugins {
    id("kscope.kotlin-library")
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
}
