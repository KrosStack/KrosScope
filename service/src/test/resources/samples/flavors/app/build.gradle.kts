plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "sample.flavors"
    compileSdk = 36

    flavorDimensions += "tier"
    productFlavors {
        create("demo") {
            dimension = "tier"
        }
        create("full") {
            dimension = "tier"
        }
    }
}
