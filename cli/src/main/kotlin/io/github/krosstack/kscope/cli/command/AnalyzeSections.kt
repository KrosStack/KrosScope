package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.requireIndex
import io.github.krosstack.kscope.cli.text.AnalyzeText
import io.github.krosstack.kscope.cli.text.UnitCardText
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.declarationsNamed
import io.github.krosstack.kscope.service.resolveNode
import io.github.krosstack.kscope.service.workspaceLocations

internal object AnalyzeSections {
    fun known(routing: Routing, scope: KrosScope): List<String> {
        val symbol = when {
            routing.reading == Reading.TASK -> false
            routing.requested -> true
            else -> isUnit(scope, routing.argument)
        }
        return if (symbol) UnitCardText.SECTIONS else AnalyzeText.SECTIONS
    }

    private fun isUnit(scope: KrosScope, argument: String): Boolean {
        scope.requireIndex()
        val node = scope.resolveNode(argument).candidates.first()
        if (scope.declarationsNamed(node).isNotEmpty()) return true
        val graph = scope.graph()
        return scope.workspaceLocations().artifactsNamed(node).any { graph.artifact(it) != null }
    }
}
