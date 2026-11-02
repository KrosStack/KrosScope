plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm()
}

compose.resources {
    packageOfResClass = "sample.cmp.resources"
    publicResClass = true
}
