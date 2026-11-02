plugins {
    id("tasktide.jvm.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":data:db"))
    implementation(project(":sync:engine"))
    implementation(project(":ui:shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "sample.tasktide.desktop.MainKt"
    }
}
