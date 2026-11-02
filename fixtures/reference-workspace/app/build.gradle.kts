plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "sample.tasktide.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "sample.tasktide"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SYNC_ENDPOINT", "\"https://sync.tasktide.example/v1\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "VERBOSE_LOGGING", "true")
        }
        release {
            buildConfigField("boolean", "VERBOSE_LOGGING", "false")
            isMinifyEnabled = false
        }
    }

    flavorDimensions += "tier"
    productFlavors {
        create("demo") {
            dimension = "tier"
            applicationIdSuffix = ".demo"
            buildConfigField("int", "MAX_TASKS", "25")
        }
        create("full") {
            dimension = "tier"
            buildConfigField("int", "MAX_TASKS", "10000")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:legacy"))
    implementation(project(":data:db"))
    implementation(project(":data:settings"))
    implementation(project(":sync:engine"))
    implementation(project(":sync:service"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:settings"))
    implementation(project(":ui:shared"))
    implementation(project(":native:bridge"))

    implementation(libs.bundles.androidx.views)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
