plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(17)
    jvm("desktop")

    android {
        compileSdk = 36
        minSdk = 24
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonTest.dependencies {
            implementation(catalog.findLibrary("kotlin-test").get())
            implementation(catalog.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
