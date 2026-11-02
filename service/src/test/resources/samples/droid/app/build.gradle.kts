plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "sample.droid.app"
}

dependencies {
    implementation(project(":lib"))
}
