plugins {
    alias(libs.plugins.acme.android.library)
}

android {
    namespace = "acme.lib"
}

dependencies {
    implementation(projects.core)
}
