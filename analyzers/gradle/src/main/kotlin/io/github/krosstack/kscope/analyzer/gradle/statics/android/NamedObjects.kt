package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.script.firstLiteral
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** The members of a Gradle container block such as `buildTypes { }`, however the DSL names them. */
internal object NamedObjects {
    private val creators = setOf(
        "create", "register", "maybeCreate", "getByName", "named", "findByName", "getting", "creating", "registering",
    )
    private val containerOperations =
        setOf("all", "configureEach", "whenObjectAdded", "withType", "matching", "forEach", "each")

    /** Whether a statement of a container block is neither a named member nor an operation on all members. */
    fun isComputed(call: ScriptCall): Boolean =
        nameOf(call) == null && !(call.receiver == null && call.name in containerOperations)

    fun entries(container: ScriptCall?): List<Pair<String, ScriptCall>> =
        container?.body.orEmpty().mapNotNull { call -> nameOf(call)?.let { it to call } }

    fun names(container: ScriptCall?): List<String> = entries(container).map { it.first }

    private fun nameOf(call: ScriptCall): String? = when {
        call.receiver != null -> null
        call.name in creators -> call.firstLiteral()
        call.name in containerOperations -> null
        else -> call.name
    }
}
