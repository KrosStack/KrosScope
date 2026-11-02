plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android) apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
    id("tasktide.android.library")
    kotlin("kapt")
    `kotlin-dsl`
    application
}

android {
    namespace = "com.example.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.app"
        minSdk = 24
        versionName = "1.0"
        buildConfigField("String", "API_URL", "\"https://example.test\"")
    }

    buildFeatures {
        viewBinding = true
        compose = false
    }

    flavorDimensions += "tier"
    productFlavors {
        create("demo") {
            dimension = "tier"
            applicationIdSuffix = ".demo"
        }
        create("full") { dimension = "tier" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(projects.core.legacy)
    implementation(projects.feature.tasks)
    implementation(libs.androidx.core)
    "kapt"(libs.room.compiler)
    "androidTestImplementation"("junit:junit:4.13.2")
    testImplementation(libs.bundles.testing)
    api(platform("com.squareup.okio:okio-bom:3.9.0"))
    if (project.hasProperty("withLeakCanary")) {
        debugImplementation(libs.leakcanary)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register<Copy>("collectDocs") {
    from("docs")
    into(layout.buildDirectory.dir("docs"))
}
