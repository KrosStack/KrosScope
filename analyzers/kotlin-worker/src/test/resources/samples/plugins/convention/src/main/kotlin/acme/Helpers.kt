package acme

import org.gradle.api.Project

internal fun Project.configureAndroidBase(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        compileSdk = 35

        defaultConfig.apply {
            minSdk = 24
        }
    }

    configureKotlin<KotlinAndroidProjectExtension>()

    dependencies {
        "coreLibraryDesugaring"(libs.findLibrary("desugar").get())
    }
}

private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    jvmTargetFor(this)
    allWarningsAsErrors = false
}

internal fun Project.jvmTargetFor(extension: Any) {
    deepest()
}

internal fun Project.deepest() {
    println("never read")
}
