package io.github.krosstack.kscope.analyzer.gradle.mapping

/**
 * The build facts about the resource class of Compose Multiplatform that both build model providers state: the
 * package the plugin was told to use, whether the class is public, and the Gradle group the default package derives
 * from. They are set for modules that apply the plugin.
 */
internal object ComposeResourceAttributes {
    const val PLUGIN_ID = "org.jetbrains.compose"
    const val PACKAGE = "compose.resources.packageOfResClass"
    const val PUBLIC = "compose.resources.publicResClass"
    const val GROUP = "compose.resources.group"

    fun of(packageOfResClass: String?, isPublic: Boolean?, group: String?): Map<String, String> = listOfNotNull(
        packageOfResClass?.takeIf { it.isNotBlank() }?.let { PACKAGE to it },
        (PUBLIC to "true").takeIf { isPublic == true },
        group?.takeIf { it.isNotBlank() }?.let { GROUP to it },
    ).toMap()
}
