package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.LineIndex
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import org.jetbrains.kotlin.psi.KtFile

/**
 * Reads the generic structure of a Kotlin build script from its syntax tree: the calls with their arguments, the
 * calls nested in lambda arguments, and the `name = value` assignments inside lambdas. Nothing is resolved or run.
 *
 * Forms that are not plain calls are encoded as calls, so consumers need one traversal:
 * - A plain assignment `name = value` inside a lambda is an assignment of the enclosing call. Every other assignment
 *   statement, as the script-level `a.b = v` or the compound `flavorDimensions += "x"`, is a call named by its
 *   operator whose receiver is the target and whose only argument is the value.
 * - Infix modifiers (`id("x") version "1" apply false`) are assignments of the call: `version` and `apply`, with the
 *   raw operand text as value.
 * - A delegated property (`val main by getting { }`) is a call named after the delegate whose first argument is the
 *   property name as a literal.
 * - A string-invoked call (`"implementation"(x)`) is a call named by the string.
 * - Calls inside `if`, `when`, `try` and loops are listed as if unconditional.
 */
internal class ScriptOutliner {
    fun outline(file: KtFile): ScriptOutline {
        val statements = file.script?.blockExpression?.statements.orEmpty()
        return ScriptOutline(OutlineReader(LineIndex(file.text)).readStatements(statements, nested = false).calls)
    }
}
