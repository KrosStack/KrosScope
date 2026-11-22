package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.model.Symbol

internal sealed interface Choice {
    data class Chosen(val symbol: Symbol) : Choice

    data class Refused(val result: DiagramResult) : Choice
}
