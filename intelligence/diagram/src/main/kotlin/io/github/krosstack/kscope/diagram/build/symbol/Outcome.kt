package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.Relationship

internal enum class Unseen {
    WITHOUT_SYMBOL,
    WITHIN_TYPE,
    NOT_IN_TYPE,
    NOT_CALLABLE,
    BEYOND_DEPTH,
    STOPPED,
}

internal data class Dropped(val origin: Relationship, val reason: Unseen)

internal sealed interface Outcome {
    val origin: Relationship

    data class Linked(val link: FlowLink) : Outcome {
        override val origin: Relationship get() = link.origin
    }

    data class Lost(val dropped: Dropped) : Outcome {
        override val origin: Relationship get() = dropped.origin
    }
}

internal fun List<Outcome>.toReading(complete: Boolean): Reading = Reading(
    filterIsInstance<Outcome.Linked>().map { it.link },
    complete,
    filterIsInstance<Outcome.Lost>().map { it.dropped },
)
