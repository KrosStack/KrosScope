plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    jvm()
}

android {
    namespace = "sample.classic"
    compileSdk = 36
}
