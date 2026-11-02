package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.Relationship

internal class Declaration(val edge: Relationship, val consumer: Module, val group: String, val artifact: String) {
    val coordinate get() = "$group:$artifact"
    val pinned get() = edge.attributes["version"]?.takeIf(String::isNotBlank)
}

internal class Outcome(val link: Relationship?, val diagnostic: Diagnostic?)
