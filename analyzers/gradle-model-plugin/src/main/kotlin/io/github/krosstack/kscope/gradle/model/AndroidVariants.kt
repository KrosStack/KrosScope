package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project

/**
 * The variants the Android Gradle plugin created, with the names of the source sets each of their components (the
 * variant itself and, where they exist, its unit tests and instrumented tests) is assembled from.
 */
internal class AndroidVariants(private val project: Project, private val state: KscopeProjectState) {
    private class Component(val name: String, val prefix: String, val classpathSuffix: String)

    fun describeAll(): List<Map<String, Any?>> =
        state.variants.mapNotNull { variant -> state.errors.guard("android", "variant") { describe(variant) } }
            .sortedBy { it["name"].toString() }

    private fun describe(variant: Any): Map<String, Any?> {
        val name = Values.text(Reflect.get(variant, "name")).orEmpty()
        val buildType = Values.text(Reflect.get(variant, "buildType"))
        val flavors = Values.items(Reflect.get(variant, "productFlavors"))
            .map { Values.text(Reflect.get(it, "second")).orEmpty() }
        return linkedMapOf(
            "name" to name,
            "buildType" to buildType,
            "flavors" to flavors,
            "namespace" to Values.text(Reflect.get(variant, "namespace")),
            "sourceSets" to sourceSetNames(name, constituents(buildType, flavors)),
            "buildConfigFields" to buildConfigFields(variant),
        )
    }

    private fun sourceSetNames(variant: String, buildTypeAndFlavors: List<String>): Map<String, List<String>> =
        COMPONENTS.filter { hasComponent(variant, it) }.associate { component ->
            val qualified = (buildTypeAndFlavors + variant).map { qualified(component.prefix, it) }
            component.name to (listOf(component.prefix.ifEmpty { MAIN }) + qualified).distinct()
        }

    private fun constituents(buildType: String?, flavors: List<String>): List<String> =
        listOfNotNull(buildType) + flavors + listOfNotNull(combined(flavors))

    private fun combined(flavors: List<String>): String? = flavors.takeIf { it.size > 1 }?.let { all ->
        all.first() + all.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
    }

    private fun hasComponent(variant: String, component: Component): Boolean =
        component.name == MAIN || project.configurations.findByName(variant + component.classpathSuffix) != null

    private fun qualified(prefix: String, name: String) =
        if (prefix.isEmpty()) name else prefix + name.replaceFirstChar { it.uppercase() }

    private fun buildConfigFields(variant: Any): Map<String, String?> {
        val fields = state.errors.guard("android", "buildConfigFields") { Reflect.get(variant, "buildConfigFields") }
        val values = (Values.items(fields).firstOrNull() as? Map<*, *>).orEmpty()
        return values.entries.associate { it.key.toString() to Values.text(Reflect.get(it.value, "type")) }
            .toSortedMap()
    }

    private companion object {
        const val MAIN = "main"
        val COMPONENTS = listOf(
            Component(MAIN, "", "CompileClasspath"),
            Component("unitTest", "test", "UnitTestCompileClasspath"),
            Component("androidTest", "androidTest", "AndroidTestCompileClasspath"),
        )
    }
}
