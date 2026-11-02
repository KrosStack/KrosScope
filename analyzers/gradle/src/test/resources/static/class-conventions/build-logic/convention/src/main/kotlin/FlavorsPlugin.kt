import org.gradle.api.Plugin
import org.gradle.api.Project

class FlavorsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<LibraryExtension> {
                configureFlavors(this)
            }
        }
    }
}
