package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.ModuleId

/** [dependencies] already include [friends] and everything the dependencies refine. */
internal class Wiring(val dependencies: List<SourceSetGroup>, val friends: List<SourceSetGroup>)

/**
 * Regular dependencies and friends of analysis modules. A variant source set outside the default group depends on the
 * default group. A test source set depends on and sees the internals of the main side of its Gradle module, and
 * inherits the dependencies of that main side. Other modules follow [ModuleDependencyRule], or, when the build model
 * has no dependency information, every module offered by the workspace that the group can compile against.
 */
internal class GroupWiring(
    private val layouts: Map<ModuleId, ModuleLayout>,
    private val graph: GroupGraph,
    private val edges: ModuleEdges,
) {
    private val moduleDependencies = ModuleDependencyRule(layouts, edges, graph)

    fun wiringOf(group: SourceSetGroup): Wiring {
        val friends = friendsOf(group)
        val inherited = friends.flatMap(::otherModulesOf)
        val defaultGroup = layouts.getValue(group.module).takeIf { it.isOutsideDefaultVariant(group) }?.defaultGroup
        val direct = otherModulesOf(group) + inherited + friends + listOfNotNull(defaultGroup)
        val all = direct.flatMap(graph::withRefined).filter { it !== group }.distinct()
        return Wiring(all, friends)
    }

    private fun otherModulesOf(group: SourceSetGroup): List<SourceSetGroup> =
        if (edges.hasInformation) moduleDependencies.dependenciesOf(group) else assumedFor(group)

    private fun assumedFor(group: SourceSetGroup): List<SourceSetGroup> =
        layouts.values.flatMap { it.offered() }.filter { it !== group && group.platform.canSee(it.platform) }

    private fun friendsOf(group: SourceSetGroup): List<SourceSetGroup> {
        if (group.isMainSide) return emptyList()
        val layout = layouts.getValue(group.module)
        val counterpart = group.members.firstNotNullOfOrNull { MainCounterpart.nameOf(it.name) }
            ?.let(layout::groupContaining)
            ?.takeIf { it.isMainSide }
        return listOfNotNull(counterpart ?: layout.defaultOrMain())
    }
}

/** The Kotlin Multiplatform main source set a test source set belongs to: `jvmTest` to `jvmMain`. */
internal object MainCounterpart {
    private val testSuffixes = listOf("InstrumentedTest", "AndroidTest", "UnitTest", "DeviceTest", "HostTest", "Test")

    fun nameOf(testSourceSet: String): String? = testSuffixes
        .firstOrNull { testSourceSet.endsWith(it) && testSourceSet.length > it.length }
        ?.let { testSourceSet.removeSuffix(it) + MAIN_SUFFIX }

    private const val MAIN_SUFFIX = "Main"
}
