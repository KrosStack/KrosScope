plugins {
    id("com.android.application")
}

android {
    namespace = "sample.dotted"
    buildFeatures.viewBinding = true
    buildFeatures.buildConfig = true
    buildFeatures.compose = false

    flavorDimensions += "tier"
    flavorDimensions += "store"
}
