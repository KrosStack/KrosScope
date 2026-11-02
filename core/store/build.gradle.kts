plugins {
    id("kscope.kotlin-library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:spi"))
    implementation(libs.sqlite.jdbc)

    testImplementation(testFixtures(project(":core:model")))
}
