plugins {
    id("com.android.library")
}

android {
    namespace = "sample.lib"
    compileSdk = libs.versions.compile.sdk.get().toInt()
}
