import org.gradle.api.Plugin
import org.gradle.api.Project

class AggregatePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(SerializationPlugin::class.java)
            apply<PingPlugin>()
            apply<AbsentPlugin>()
            dependencies {
                add("implementation", project(":core"))
                "implementation"("org.example:library:1.0")
            }
        }
    }
}

class PingPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.apply<PongPlugin>()
    }
}

class PongPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.apply<PingPlugin>()
    }
}
