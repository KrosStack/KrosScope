import org.gradle.api.Plugin
import org.gradle.api.Project

class DynamicPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val extra = providers.gradleProperty("extraPlugin").get()
            apply(plugin = extra)
            pluginManager.withPlugin("com.android.base") {
                apply(plugin = "acme.base.extra")
            }
            listOf("a", "b").forEach { name ->
                dependencies { "implementation"(project(":$name")) }
            }
            dependencies {
                "implementation"(project(":core"))
                "implementation"(project(path))
            }
        }
    }
}
