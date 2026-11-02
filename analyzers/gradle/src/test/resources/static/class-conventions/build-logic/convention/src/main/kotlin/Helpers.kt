import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        compileSdk = 35

        defaultConfig.apply {
            minSdk = 24
        }

        buildFeatures.apply {
            buildConfig = true
        }
    }

    dependencies {
        "coreLibraryDesugaring"(libs.findLibrary("desugar.jdk.libs").get())
    }
}

internal fun Project.configureCompose(commonExtension: CommonExtension) {
    commonExtension.apply {
        buildFeatures.apply {
            compose = true
        }
        dependencies {
            val bom = libs.findLibrary("androidx-compose-bom").get()
            "implementation"(platform(bom))
            "debugImplementation"(libs.findLibrary("androidx.compose.ui").get())
        }
    }
    commonExtension.buildFeatures.apply {
        viewBinding = true
    }
    commonExtension.testOptions.apply {
        unitTests.all { it.systemProperty("flag", "true") }
    }
}

enum class AcmeFlavor { demo, prod }

internal fun configureFlavors(commonExtension: CommonExtension) {
    commonExtension.apply {
        productFlavors {
            AcmeFlavor.entries.forEach { flavor ->
                register(flavor.name) {
                    dimension = "tier"
                }
            }
        }
    }
}
