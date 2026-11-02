plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm("desktop")

    android {
        namespace = "sample.shared"
        compileSdk = 36
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":data"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        named("desktopMain") {
            dependencies {
                implementation("org.xerial:sqlite-jdbc:3.53.4.0")
            }
        }
        val shared by creating {
            dependsOn(commonMain.get())
        }
        val desktopMain by getting {
            dependsOn(shared)
        }
    }
}
