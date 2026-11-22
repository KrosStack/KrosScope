package io.github.krosstack.kscope.cli.diagram

import io.github.krosstack.kscope.cli.text.format.NodeLabels
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.model.Symbol

internal object DiagramMessages {
    fun notFound(kind: DiagramKind, node: String?): String = when {
        node == null -> "No node was named."
        kind == DiagramKind.MODULES -> "No module matches \"$node\". Name a module by its path or id."
        kind == DiagramKind.COMPONENTS -> "No repository matches \"$node\". Name a repository by its name or id."
        else -> "No declaration matches \"$node\"."
    }

    fun notSupported(symbol: Symbol): String =
        "${NodeLabels.symbol(symbol.id)} is a ${symbol.kind.id}, which a diagram does not draw. " +
            "Name a type or a function."

    fun tooSmall(characters: Int, minimum: Int): String =
        "--characters $characters is too small; the diagram needs at least $minimum characters."
}
