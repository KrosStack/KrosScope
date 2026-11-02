plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "sample.aidl.svc"

    buildFeatures {
        aidl = true
    }
}
