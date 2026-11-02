package io.github.krosstack.kscope.analyzer.gradle.statics.layout

/** A main source set of the hierarchy: what it depends on, and the names of the targets it is compiled for. */
internal class HierarchyNode(val name: String, val parents: List<String>, val targets: List<String>)

private const val COMMON = "commonMain"

private class Group(val name: String, val factories: Set<String>) {
    fun isBelow(other: Group): Boolean = other.factories.containsAll(factories) && other.factories != factories
}

private val ios = setOf("iosArm64", "iosX64", "iosSimulatorArm64")
private val macos = setOf("macosX64", "macosArm64")
private val tvos = setOf("tvosArm64", "tvosX64", "tvosSimulatorArm64")
private val watchos = setOf("watchosArm32", "watchosArm64", "watchosX64", "watchosSimulatorArm64", "watchosDeviceArm64")
private val linux = setOf("linuxX64", "linuxArm64")
private val mingw = setOf("mingwX64")
private val androidNative = setOf("androidNativeArm32", "androidNativeArm64", "androidNativeX86", "androidNativeX64")
private val apple = ios + macos + tvos + watchos

private val groups: List<Group> = listOf(
    Group("native", apple + linux + mingw + androidNative),
    Group("apple", apple),
    Group("ios", ios),
    Group("macos", macos),
    Group("tvos", tvos),
    Group("watchos", watchos),
    Group("linux", linux),
    Group("mingw", mingw),
    Group("androidNative", androidNative),
    Group("wasm", setOf("wasmJs", "wasmWasi")),
    Group("web", setOf("js", "wasmJs")),
)

/**
 * The source sets of the default hierarchy template of the Kotlin Gradle plugin for a set of targets: `commonMain`, a
 * main source set per target, and a main source set per group of the template that has two or more of the targets.
 * Every source set depends on the closest groups above it, or on `commonMain` when there are none.
 */
internal object DefaultHierarchy {
    fun plan(targets: List<TargetSpec>): List<HierarchyNode> {
        val present = groups.filter { group -> targets.count { it.factory in group.factories } >= 2 }
        val common = HierarchyNode(COMMON, emptyList(), targets.map { it.name })
        val groupNodes = present.map { group ->
            val members = targets.filter { it.factory in group.factories }.map { it.name }
            HierarchyNode(mainOf(group.name), parentsOf(present.filter { group.isBelow(it) }), members)
        }
        val targetNodes = targets.map { target ->
            val groupsOfTarget = present.filter { target.factory in it.factories }
            HierarchyNode(mainOf(target.name), parentsOf(groupsOfTarget), listOf(target.name))
        }
        return listOf(common) + groupNodes + targetNodes
    }

    fun mainOf(name: String): String = if (name == COMMON) name else "${name}Main"

    private fun parentsOf(ancestors: List<Group>): List<String> {
        val closest = ancestors.filter { candidate -> ancestors.none { it.isBelow(candidate) } }
        return closest.map { mainOf(it.name) }.ifEmpty { listOf(COMMON) }
    }
}
