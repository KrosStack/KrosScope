plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "sample.viewdroid.app"

    defaultConfig {
        buildConfigField("String", "ENDPOINT", "\"https://example.test\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "VERBOSE", "true")
        }
    }
}

dependencies {
    implementation(project(":lib"))
}
