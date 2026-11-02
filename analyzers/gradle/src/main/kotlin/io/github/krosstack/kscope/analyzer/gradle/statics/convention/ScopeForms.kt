package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.spi.worker.ScriptCall

private val scopeFunctions = setOf("apply", "run")
private val controlCalls = setOf("if", "when", "for", "while", "do", "try")
private val relevantCalls = setOf("apply", "configure", "dependencies", "getByType", "withPlugin", "withId", "add")
private val simpleIdentifier = Regex("""[A-Za-z_]\w*""")

/** How the statements of a plugin's `apply` pick the scope they configure. */
internal object ScopeForms {
    fun isControl(call: ScriptCall): Boolean = call.receiver == null && call.name in controlCalls

    /**
     * The scope a statement opens: `configure<T> { }` on the extensions, `x.apply { }` and `x.run { }` on a name or an
     * expression that reads an extension, and `with(x) { }`. Null when the statement opens none.
     */
    fun opened(call: ScriptCall, env: Env): Scope? = when {
        isConfigure(call, env) -> ExtensionScopes.ofType(call.typeArguments.single()) ?: Scope.OTHER
        call.name in scopeFunctions -> call.receiver?.let(env::scopeOf)
        call.name == "with" && call.receiver == null -> call.arguments.firstOrNull()?.let { env.scopeOf(it.expression) }
        else -> null
    }

    /**
     * The scope and the member that `x.member.apply { }` configures, when `x` stands for an extension of that scope:
     * `buildFeatures` for `commonExtension.buildFeatures.apply { }`.
     */
    fun memberOf(call: ScriptCall, env: Env): Pair<Scope, String>? {
        val segments = call.receiver?.split('.').orEmpty()
        val isMember = call.name in scopeFunctions && segments.size > 1 && segments.all(simpleIdentifier::matches)
        val scope = segments.firstOrNull()?.let(env::scopeOf)?.takeIf { it == Scope.ANDROID || it == Scope.KOTLIN }
        return scope?.takeIf { isMember }?.let { it to segments.drop(1).joinToString(".") }
    }

    /** Whether a body applies plugins, configures extensions or declares dependencies, however deeply nested. */
    fun isRelevant(calls: List<ScriptCall>): Boolean =
        calls.any { it.name in relevantCalls || it.name in controlCalls || it.inlined || isRelevant(it.body) }

    /** Adds a statement of an extension block to [block]; `x.apply { }` becomes the block `x { }`. */
    fun addVerbatim(call: ScriptCall, env: Env, block: BlockBuilder) {
        val receiver = call.receiver
        when {
            call.name == "=" && receiver != null -> block.assign(mapOf(receiver to call.arguments.first().expression))

            call.name in scopeFunctions && receiver != null && isPlainName(receiver, env) ->
                block.add(call.copy(name = receiver, receiver = null))

            else -> block.add(call)
        }
    }

    private fun isConfigure(call: ScriptCall, env: Env): Boolean {
        val receiver = call.receiver
        val onExtensions = receiver == null || receiver.split('.').dropWhile { env.scopeOf(it) == Scope.PROJECT } ==
            listOf("extensions")
        return call.name == "configure" && call.typeArguments.size == 1 && onExtensions
    }

    private fun isPlainName(receiver: String, env: Env): Boolean =
        simpleIdentifier.matches(receiver) && env.scopeOf(receiver) == null
}
