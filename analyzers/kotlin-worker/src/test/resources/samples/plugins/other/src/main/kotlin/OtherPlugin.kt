import org.gradle.api.Plugin
import org.gradle.api.Project

class OtherPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("acme.other")
        target.configureAndroidBase(target)
    }
}
