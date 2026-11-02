package io.github.krosstack.kscope.analyzer.compose.res

import io.github.krosstack.kscope.model.Module

internal const val PACKAGE_ATTRIBUTE = "compose.resources.packageOfResClass"
internal const val PUBLIC_ATTRIBUTE = "compose.resources.publicResClass"
internal const val GROUP_ATTRIBUTE = "compose.resources.group"
internal const val COMPOSE_PLUGIN_ID = "org.jetbrains.compose"

private const val ROOT_NAME_ATTRIBUTE = "gradle.rootProject.name"
private const val ROOT_PATH = ":"
private const val DEFAULT_SUFFIX = "generated.resources"

/** The package of the resource class of a module. [isExplicit] is false when the plugin's default was derived. */
internal class ResPackage(val name: String, val isExplicit: Boolean)

/**
 * The package of the resource class, the way the Compose Multiplatform plugin decides it: the package the build sets,
 * or `<group>.<module name>.generated.resources`, both in lower case, with `-` written `_` and a leading digit
 * protected by an underscore. The group and the name of a module are the ones Gradle gives the project.
 */
internal object ResPackages {
    fun of(module: Module): ResPackage? {
        val explicit = module.attributes[PACKAGE_ATTRIBUTE]?.takeIf { it.isNotBlank() }
        val name = explicit ?: derivedName(module)
        return if (KotlinNames.isPackage(name)) ResPackage(name, isExplicit = explicit != null) else null
    }

    private fun derivedName(module: Module): String {
        val group = underscored(module.attributes[GROUP_ATTRIBUTE].orEmpty().lowercase())
        val id = listOf(group, underscored(projectNameOf(module).lowercase())).filter { it.isNotEmpty() }
        return id.joinToString(".") + ".$DEFAULT_SUFFIX"
    }

    private fun projectNameOf(module: Module): String = if (module.path == ROOT_PATH) {
        module.attributes[ROOT_NAME_ATTRIBUTE] ?: module.directory.value.substringAfterLast('/')
    } else {
        module.path.substringAfterLast(':')
    }

    private fun underscored(text: String): String {
        val replaced = text.replace('-', '_')
        return if (replaced.firstOrNull()?.isDigit() == true) "_$replaced" else replaced
    }
}
