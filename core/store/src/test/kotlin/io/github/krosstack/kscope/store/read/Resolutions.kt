package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.store.Builders

internal object Resolutions {
    fun resolved(to: String, certainty: Certainty = Certainty.EXACT) =
        Resolution.Resolved(Builders.symbolRef(to), certainty)

    fun unresolved(hint: String = "missing") = Resolution.Unresolved(hint, ReasonCode("test.unresolved"))

    fun notAnalysed(hint: String = "native") = Resolution.NotAnalysed(hint, ReasonCode("test.not-analysed"))
}
