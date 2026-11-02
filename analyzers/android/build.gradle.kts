plugins {
    id("kscope.kotlin-library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:spi"))

    testImplementation(testFixtures(project(":core:model")))
}
