import org.gradle.api.Plugin
import org.gradle.api.Project

class FeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")
            dependencies {
                "implementation"(project(":core"))
            }
        }
    }
}
