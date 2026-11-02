plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "sample.tasktide"
version = "1.2.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
