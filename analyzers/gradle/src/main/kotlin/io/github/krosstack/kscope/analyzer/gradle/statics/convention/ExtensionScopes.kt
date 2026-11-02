package io.github.krosstack.kscope.analyzer.gradle.statics.convention

/** What the statements of a body configure: the project, the Android extension, the Kotlin extension, or neither. */
internal enum class Scope { PROJECT, ANDROID, KOTLIN, OTHER }

private val androidExtensions = setOf(
    "ApplicationExtension",
    "LibraryExtension",
    "TestExtension",
    "DynamicFeatureExtension",
    "CommonExtension",
    "BaseExtension",
    "AppExtension",
    "BaseAppModuleExtension",
    "TestedExtension",
)

private val otherExtensions = setOf(
    "KotlinAndroidProjectExtension",
    "KotlinJvmProjectExtension",
    "KotlinBaseExtension",
    "JavaPluginExtension",
    "ExtraPropertiesExtension",
    "VersionCatalogsExtension",
    "ApplicationAndroidComponentsExtension",
    "LibraryAndroidComponentsExtension",
    "AndroidComponentsExtension",
    "ComposeCompilerGradlePluginExtension",
    "KspExtension",
    "RoomExtension",
    "SpotlessExtension",
    "Lint",
)

private val typedAccess = Regex("""^(?:[\w.]+\.)?(?:getByType|findByType|the)\s*<\s*(.+?)\s*>\s*\(\s*\)$""")
private val classAccess = Regex("""^(?:[\w.]+\.)?(?:getByType|findByType)\s*\(\s*([\w.]+)::class(?:\.java)?\s*\)$""")

/** The scope a Gradle extension type or an expression that reads an extension stands for. */
internal object ExtensionScopes {
    fun ofType(type: String?): Scope? {
        val simple = type?.substringBefore('<')?.trim()?.removeSuffix("?")?.substringAfterLast('.').orEmpty()
        return when {
            simple == "Project" -> Scope.PROJECT
            simple in androidExtensions -> Scope.ANDROID
            simple == "KotlinMultiplatformExtension" -> Scope.KOTLIN
            simple in otherExtensions -> Scope.OTHER
            else -> null
        }
    }

    /** The type that `extensions.getByType<T>()` and its siblings read, as written. */
    fun typeRead(expression: String): String? =
        (typedAccess.matchEntire(expression.trim()) ?: classAccess.matchEntire(expression.trim()))?.groupValues?.get(1)
}
