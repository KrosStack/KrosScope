plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    dependencies {
        implementation(project(":lib"))
        implementation("org.slf4j:slf4j-api:2.0.17")
    }

    jvmToolchain(21)
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
