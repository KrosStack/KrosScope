plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(libs.kotlinx.coroutines.core)
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.xerial:sqlite-jdbc:3.53.4.0")
            }
        }
        named("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
