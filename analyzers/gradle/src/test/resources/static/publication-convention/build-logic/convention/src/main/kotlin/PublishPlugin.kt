import org.gradle.api.Plugin
import org.gradle.api.Project

class PublishPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("maven-publish")
        target.group = "org.example.library"
        target.version = "1.2.0"
    }
}
