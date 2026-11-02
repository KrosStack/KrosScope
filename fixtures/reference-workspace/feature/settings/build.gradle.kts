plugins {
    id("tasktide.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "sample.tasktide.feature.settings"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":data:settings"))
    implementation(project(":ui:shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
}
