plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "sample.aidl.app"
}

dependencies {
    implementation(project(":svc"))
}
