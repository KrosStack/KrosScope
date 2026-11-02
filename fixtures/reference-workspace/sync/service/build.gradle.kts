plugins {
    id("tasktide.android.library")
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "sample.tasktide.sync.service"

    buildFeatures {
        aidl = true
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":sync:engine"))
}
