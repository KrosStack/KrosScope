plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "sample.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "sample.app"
        minSdk = 24
        buildConfigField("String", "SYNC_ENDPOINT", "https://sync.example/v1")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
        aidl = false
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "VERBOSE_LOGGING", "true")
        }
        release {
            isMinifyEnabled = false
        }
        create("staging") {
            initWith(getByName("debug"))
        }
    }

    flavorDimensions += "tier"
    productFlavors {
        create("demo") {
            dimension = "tier"
            buildConfigField("int", "MAX_TASKS", "25")
        }
        create("full") {
            dimension = "tier"
        }
    }
}
