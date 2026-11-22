package io.github.krosstack.kscope.cli.serve.map

/** Reads the parameters of a diagram question and refuses what is out of range before the serve thread is asked. */
internal object DiagramAsks {
    const val DEFAULT_LINKS = 60
    const val MAX_LINKS = 1_000
    const val MAX_DEPTH = 50
    const val ALL = "all"
    private const val DEFAULT_DEPTH = 1
    private const val EVERY_STEP = 0

    fun of(values: Map<String, String>): DiagramAsk {
        val node = values["node"]?.takeIf { it.isNotBlank() }
        val steps = stepsOf(values["depth"])
        val links = linksOf(values["links"])
        return when {
            node == null -> DiagramAsk.Refused("Name a symbol with node.")
            steps == null -> DiagramAsk.Refused("depth is a whole number from 1 to $MAX_DEPTH, or $ALL.")
            links == null -> DiagramAsk.Refused("links is a whole number from 0 to $MAX_LINKS.")
            else -> DiagramAsk.Valid(node, steps.takeIf { it != EVERY_STEP }, links)
        }
    }

    private fun stepsOf(text: String?): Int? = when (text) {
        null -> DEFAULT_DEPTH
        ALL -> EVERY_STEP
        else -> text.toIntOrNull()?.takeIf { it in 1..MAX_DEPTH }
    }

    private fun linksOf(text: String?): Int? =
        if (text == null) DEFAULT_LINKS else text.toIntOrNull()?.takeIf { it in 0..MAX_LINKS }
}
