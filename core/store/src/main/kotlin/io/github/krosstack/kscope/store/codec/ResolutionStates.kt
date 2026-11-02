package io.github.krosstack.kscope.store.codec

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor

internal object ResolutionStates {
    const val UNRESOLVED = "unresolved"
    const val NOT_ANALYSED = "not-analysed"

    val inReportingOrder: List<String> = Certainty.entries.map(Wires.certainty::nameOf) + UNRESOLVED + NOT_ANALYSED
}

internal fun resolutionColumns(): List<String> = listOf(
    "res_state TEXT NOT NULL",
    "res_target_kind TEXT",
    "res_target_key TEXT",
    "res_hint TEXT",
    "res_reason TEXT",
)

internal fun Binder.resolution(resolution: Resolution) {
    when (resolution) {
        is Resolution.Resolved -> {
            text(Wires.certainty.nameOf(resolution.certainty))
            nodeRef(resolution.target)
            hintAndReason(null, null)
        }

        is Resolution.Unresolved -> unresolved(ResolutionStates.UNRESOLVED, resolution.hint, resolution.reason)

        is Resolution.NotAnalysed -> unresolved(ResolutionStates.NOT_ANALYSED, resolution.hint, resolution.reason)
    }
}

internal fun Cursor.resolution(): Resolution {
    val state = text()
    val target = nodeRefOrNull()
    val hint = textOrNull()
    val reason = textOrNull()
    return when (state) {
        ResolutionStates.UNRESOLVED -> Resolution.Unresolved(checkNotNull(hint), ReasonCode(checkNotNull(reason)))
        ResolutionStates.NOT_ANALYSED -> Resolution.NotAnalysed(checkNotNull(hint), ReasonCode(checkNotNull(reason)))
        else -> Resolution.Resolved(checkNotNull(target), Wires.certainty.valueOf(state))
    }
}

private fun Binder.unresolved(state: String, hint: String, reason: ReasonCode) {
    text(state)
    nodeRefOrNull(null)
    hintAndReason(hint, reason.value)
}

private fun Binder.hintAndReason(hint: String?, reason: String?) {
    textOrNull(hint)
    textOrNull(reason)
}
