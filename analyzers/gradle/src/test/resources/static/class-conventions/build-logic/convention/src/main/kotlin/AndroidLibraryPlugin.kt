import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")
            apply(plugin = "acme.lint")

            extensions.configure<LibraryExtension> {
                configureAndroid(this)
                defaultConfig.targetSdk = 35
            }
            dependencies {
                "testImplementation"(libs.findLibrary("junit").get())
                "implementation"(libs.findLibrary("androidx.tracing.ktx").get())
                add("testImplementation", libs.findBundle("testing").get())
            }
        }
    }
}
