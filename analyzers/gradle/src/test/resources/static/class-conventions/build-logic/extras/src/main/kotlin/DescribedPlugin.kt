import org.gradle.api.Plugin
import org.gradle.api.Project

class DescribedPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("java-library")
    }
}
