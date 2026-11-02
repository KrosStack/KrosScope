plugins {
    id("kscope.kotlin-library")
}

dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(libs.detekt.test)
}
