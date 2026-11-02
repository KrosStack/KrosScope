plugins {
    id("tasktide.android.library")
}

android {
    namespace = "sample.tasktide.feature.tasks"

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:legacy"))
    implementation(libs.bundles.androidx.views)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.navigation.fragment)
}
