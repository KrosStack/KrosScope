package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.statics.layout.upperFirst

private val testPrefixes = listOf("androidTest", "testFixtures", "test")
private val sourceSetName = Regex("""[a-z][A-Za-z0-9]*""")
private const val MAIN = "main"

/**
 * The product flavors of an Android project whose script computes them, taken from its source set directories.
 * A directory names a flavor unless it is `main`, a test source set without a flavor, or a build type. The directory
 * of a variant, `freeDebug`, and of a test source set, `testFree`, name the flavor they are made of. It cannot tell a
 * flavor from a source set or a build type that a plugin or a script names by hand, and it cannot see a flavor
 * without a directory.
 */
internal object FlavorDirectories {
    fun inferred(config: AndroidConfig, directories: List<String>): List<String> {
        val declared = config.flavors.map { it.name }.toSet()
        val named = directories.filter { it != MAIN && sourceSetName.matches(it) }
        return named.mapNotNull { flavorOf(it, config.buildTypes) }.filter { it !in declared }.distinct().sorted()
    }

    private fun flavorOf(directory: String, buildTypes: List<String>): String? {
        val name = afterTestPrefix(directory) ?: return null
        val variantOf = buildTypes.firstOrNull { name.endsWith(it.upperFirst()) }
        val flavor = if (variantOf == null) name else name.removeSuffix(variantOf.upperFirst())
        return flavor.takeIf { it !in buildTypes }
    }

    private fun afterTestPrefix(directory: String): String? {
        val prefix = testPrefixes.firstOrNull { isTestSetOf(directory, it) }
        return when {
            prefix == null -> directory
            directory == prefix -> null
            else -> directory.removePrefix(prefix).replaceFirstChar { it.lowercase() }
        }
    }

    private fun isTestSetOf(directory: String, prefix: String): Boolean =
        directory == prefix || (directory.startsWith(prefix) && directory[prefix.length].isUpperCase())
}
