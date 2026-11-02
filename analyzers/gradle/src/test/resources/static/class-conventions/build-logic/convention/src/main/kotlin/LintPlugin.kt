import org.gradle.api.Plugin
import org.gradle.api.Project

class LintPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            when {
                pluginManager.hasPlugin("com.android.application") ->
                    configure<ApplicationExtension> { lint { checkDependencies = true } }

                else -> apply(plugin = "com.android.lint")
            }
        }
    }
}
