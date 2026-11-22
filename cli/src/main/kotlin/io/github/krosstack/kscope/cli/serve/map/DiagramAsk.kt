package io.github.krosstack.kscope.cli.serve.map

/** What a request for a diagram asks. A null [Valid.depth] asks for as far as the budgets of the diagram reach. */
internal sealed interface DiagramAsk {
    data class Valid(val node: String, val depth: Int?, val links: Int) : DiagramAsk

    data class Refused(val message: String) : DiagramAsk
}
