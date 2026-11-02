plugins {
    id("tasktide.kmp.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "sample.tasktide.ui.shared"
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "sample.tasktide.ui.shared.resources"
    publicResClass = true
}
