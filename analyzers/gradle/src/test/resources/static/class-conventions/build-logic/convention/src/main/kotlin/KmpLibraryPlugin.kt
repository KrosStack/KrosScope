import org.gradle.api.Plugin
import org.gradle.api.Project

class KmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
            extensions.configure<KotlinMultiplatformExtension> {
                jvm()
                iosArm64()
            }
        }
    }
}
