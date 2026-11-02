plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvm("desktop")
    androidTarget()
    iosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                api(project(":core:model"))
            }
        }
        val desktopMain by getting {
            dependsOn(commonMain)
        }
        val androidMain by getting
        val commonTest by creating {
            dependsOn(commonMain)
        }
        commonMain.dependencies {
            implementation(libs.ktor.client)
        }
        androidMain {
            dependsOn(commonMain)
        }
    }
}

android {
    namespace = "com.example.lib"
    compileSdk = 36
}

group = "com.example"
version = "1.2.3"
