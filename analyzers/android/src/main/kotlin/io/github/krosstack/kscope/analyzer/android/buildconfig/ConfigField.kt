package io.github.krosstack.kscope.analyzer.android.buildconfig

import io.github.krosstack.kscope.analyzer.android.rclass.JavaNames

internal const val BUILD_CONFIG_FIELD_PREFIX = "android.buildConfigField."
internal const val FLAVOR_DIMENSIONS_ATTRIBUTE = "android.flavorDimensions"

private val primitiveNumbers = setOf("byte", "short", "int", "long", "float", "double", "char")
private val typeSyntax = Regex("""[A-Za-z_][\w.]*(<[\w.,<>\[\]? ]*>)?(\[])*""")

/** A constant of `BuildConfig`: the Java [type] and a value that is valid for it; the stub's values mean nothing. */
internal class ConfigField(val name: String, val type: String) {
    val signature: String get() = "public static final $type $name"

    val value: String
        get() = when {
            type == "boolean" -> "false"
            type in primitiveNumbers -> "0"
            type == "String" -> "\"\""
            else -> "null"
        }
}

/** The constants that the Android Gradle plugin puts in `BuildConfig`, and those that a build script adds. */
internal object ConfigFields {
    fun standard(isApplication: Boolean, hasFlavors: Boolean, dimensions: List<String>): List<ConfigField> {
        val identity = if (isApplication) "APPLICATION_ID" else "LIBRARY_PACKAGE_NAME"
        val flavors = if (hasFlavors) listOf("FLAVOR") + dimensions.map { "FLAVOR_$it" } else emptyList()
        val strings = (listOf(identity, "BUILD_TYPE") + flavors).map { ConfigField(it, "String") }
        val versions = if (isApplication) {
            listOf(ConfigField("VERSION_CODE", "int"), ConfigField("VERSION_NAME", "String"))
        } else {
            emptyList()
        }
        return (listOf(ConfigField("DEBUG", "boolean")) + strings + versions).filter { JavaNames.isIdentifier(it.name) }
    }

    /** The fields declared by `buildConfigField`, by name; a name or type that Java cannot take is left out. */
    fun declared(attributes: Map<String, String>): List<ConfigField> = attributes
        .filterKeys { it.startsWith(BUILD_CONFIG_FIELD_PREFIX) }
        .map { (key, type) -> ConfigField(key.removePrefix(BUILD_CONFIG_FIELD_PREFIX), type.trim()) }
        .sortedBy { it.name }

    fun isUsable(field: ConfigField): Boolean = JavaNames.isIdentifier(field.name) && typeSyntax.matches(field.type)
}
