package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.ROOT
import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.edgesOf
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution

/** The checked-in build tree whose conventions are plugin classes, provided once and removed afterwards. */
internal class ClassConventionTree : AutoCloseable {
    private val tree = StaticWorkspace("class-conventions")
    val provided = tree.provided()

    /** The (target, configuration) pairs of the dependencies of a module: `:path` for modules, else a coordinate. */
    fun dependenciesOf(path: String): List<Pair<String, String?>> = provided.edgesOf(path).map { edge ->
        val target = (edge.resolution as Resolution.Resolved).target
        val name = when (target) {
            is NodeRef.OfExternal -> target.coordinate
            is NodeRef.OfModule -> target.id.value.removePrefix(ROOT)
            else -> error("unexpected target")
        }
        name to edge.attributes["configuration"]
    }.sortedWith(compareBy({ it.first }, { it.second }))

    override fun close() = tree.close()

    companion object {
        const val LOGIC = "build-logic/convention/src/main/kotlin"
    }
}
