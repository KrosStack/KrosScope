plugins {
    id("kscope.kotlin-library")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:spi"))
    implementation(libs.kotlinx.serialization.core)
}
