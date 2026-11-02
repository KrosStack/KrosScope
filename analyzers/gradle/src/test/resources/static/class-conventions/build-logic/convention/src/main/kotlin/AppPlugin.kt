import org.gradle.api.Plugin
import org.gradle.api.Project

class AppPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.apply {
            apply(plugin = "com.android.application")
            extensions.getByType<ApplicationExtension>().apply {
                compileSdk = 36
                defaultConfig.targetSdk = 36
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                beforeVariants { it.enable = true }
            }
        }
    }
}
