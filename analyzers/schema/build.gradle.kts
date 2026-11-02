plugins {
    id("kscope.kotlin-library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:spi"))
    implementation(libs.wire.schema)
    implementation(libs.jsqlparser)

    testImplementation(testFixtures(project(":core:model")))
}
