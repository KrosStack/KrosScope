package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.script.positional
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** The declarations of a `dependencies { }` block that can be read, and what could not be. */
internal class DependencyBlock(val declarations: List<ScriptCall>, val reasons: List<String>)

private val notationForms = listOf(
    Regex("""^"[^"$\\]+"$"""),
    Regex("""^project\(\s*(?:path\s*[:=]\s*)?"(:[^"$]*)"[^)]*\)$"""),
    Regex("""^[A-Za-z_]\w*\.find(?:Library|Bundle)\(\s*"[^"$]+"\s*\)(?:\.get\(\))?$"""),
    Regex("""^[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+$"""),
    Regex("""^(?:files|fileTree|gradleApi|gradleKotlinDsl|localGroovy|kotlin)\(.*\)$"""),
)
private val wrapper = Regex("""^(?:platform|enforcedPlatform|testFixtures)\((.*)\)$""")
private val quotedOrLocal = Regex(""""[^"]*"|(?<![\w.])[A-Za-z_]\w*(?![\w.(])""")
private val controlCalls = setOf("if", "when", "for", "while", "do", "try")
private val notConfigurations = setOf("constraints", "components", "modules", "attributesSchema", "artifactTypes")

/**
 * Reads the statements of a `dependencies { }` block of a plugin: `"conf"(notation)`, `conf(notation)` and
 * `add("conf", notation)`, whose notation is a coordinate, `project(":path")`, a catalog accessor or a lookup by alias
 * (`libs.findLibrary("alias").get()`), possibly wrapped in `platform(...)` or read from a local.
 */
internal object DependencyForms {
    const val COMPUTED = "declares dependencies whose configuration or notation is computed"
    const val CONDITIONAL = "declares dependencies in a conditional block or a loop"

    fun read(block: ScriptCall, env: Env): DependencyBlock {
        var locals = env
        val declarations = mutableListOf<ScriptCall>()
        val reasons = linkedSetOf<String>()
        block.body.forEach { statement ->
            when {
                statement.name == "val" -> locals = locals.declare(statement)

                isDeclaration(statement) -> {
                    val declared = declaration(statement, locals)
                    if (declared == null) reasons.add(COMPUTED) else declarations.add(declared)
                }

                isComputedBlock(statement) -> reasons.add(CONDITIONAL)
            }
        }
        return DependencyBlock(declarations, reasons.toList())
    }

    private fun isDeclaration(statement: ScriptCall): Boolean = statement.receiver == null &&
        statement.name !in controlCalls && statement.name !in notConfigurations &&
        (statement.name == "add" || statement.arguments.isNotEmpty())

    private fun isComputedBlock(statement: ScriptCall): Boolean = statement.name in controlCalls ||
        (statement.name !in notConfigurations && statement.body.isNotEmpty() && statement.arguments.isEmpty())

    private fun declaration(statement: ScriptCall, env: Env): ScriptCall? {
        val positional = statement.positional()
        val isAdd = statement.name == "add"
        val notation = positional.getOrNull(if (isAdd) 1 else 0) ?: return null
        val configurationIsLiteral = !isAdd || positional.firstOrNull()?.literal != null
        val text = substituted(notation.expression, env)
        return if (configurationIsLiteral && isReadable(text)) statement.withNotation(notation, text) else null
    }

    private fun ScriptCall.withNotation(original: ScriptArgument, text: String): ScriptCall {
        if (text == original.expression.trim()) return this
        val replaced = original.copy(literal = null, expression = text)
        return copy(arguments = arguments.map { if (it === original) replaced else it })
    }

    private fun substituted(expression: String, env: Env): String =
        quotedOrLocal.replace(expression.trim()) { match -> env.valueOf(match.value) ?: match.value }

    private fun isReadable(text: String): Boolean {
        val inner = wrapper.matchEntire(text)?.groupValues?.get(1)?.trim()
        return if (inner != null) isReadable(inner) else notationForms.any { it.matches(text) }
    }
}
