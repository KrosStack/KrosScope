plugins {
    id("com.android.library")
}

android {
    namespace = "sample.multi"
    compileSdk {
        version = release(36)
    }

    flavorDimensions("tier", "store")
    productFlavors {
        register("free") {
            dimension = "tier"
        }
        register("paid") {
            dimension = "tier"
        }
        register("play") {
            dimension = "store"
        }
        register("galaxy") {
            dimension = "store"
        }
    }
}
