plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    linuxX64()
    js()
    wasmJs()
}
