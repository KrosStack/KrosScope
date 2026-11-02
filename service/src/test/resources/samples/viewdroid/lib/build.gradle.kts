plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "sample.viewdroid.lib"

    buildFeatures {
        buildConfig = true
    }
}
