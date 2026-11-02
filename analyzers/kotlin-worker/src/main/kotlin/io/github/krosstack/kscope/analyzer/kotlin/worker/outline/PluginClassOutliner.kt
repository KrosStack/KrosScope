package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.spi.worker.PluginClassOutline
import io.github.krosstack.kscope.spi.worker.ScriptCall
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

private val pluginSupertype = Regex("""^(?:[A-Za-z0-9_]+\.)*Plugin<""")
private const val APPLY = "apply"

/**
 * Finds the classes of a Kotlin source whose supertypes name `Plugin<...>` and reads the body of their `apply`
 * function. The functions of [index] that the body calls are inlined.
 */
internal class PluginClassOutliner(private val index: FunctionIndex) {
    fun outline(file: KtFile): List<PluginClassOutline> =
        pluginClasses(file.declarations, file.packageFqName.asString(), ".").mapNotNull(::outlineOf)

    private class Candidate(val declaration: KtClass, val qualifiedName: String)

    private fun pluginClasses(declarations: List<KtDeclaration>, prefix: String, separator: String): List<Candidate> =
        declarations.filterIsInstance<KtClass>().flatMap { declaration ->
            val name = declaration.name.orEmpty()
            val qualified = if (prefix.isEmpty()) name else prefix + separator + name
            listOfNotNull(Candidate(declaration, qualified).takeIf { implementsPlugin(declaration) }) +
                pluginClasses(declaration.declarations, qualified, "$")
        }

    private fun implementsPlugin(declaration: KtClass): Boolean {
        val supertypes = declaration.superTypeListEntries.mapNotNull { it.typeReference?.text }
        return !declaration.isInterface() && supertypes.any(pluginSupertype::containsMatchIn)
    }

    private fun outlineOf(candidate: Candidate): PluginClassOutline? {
        val function = candidate.declaration.declarations.filterIsInstance<KtNamedFunction>()
            .firstOrNull { it.name == APPLY && it.namedParameters().size == 1 } ?: return null
        val file = candidate.declaration.containingKtFile
        val lines = index.lines(file)
        val target = function.namedParameters().single()
        val inliner = FunctionInliner(index)
        val scope = ReadScope(0, emptyList(), setOfNotNull(target.name, "this"))
        val reader = OutlineReader(lines, SourceForms(lines, inliner, scope))
        val statements = function.bodyBlockExpression?.statements ?: listOfNotNull(function.bodyExpression)
        val range = lines.range(candidate.declaration.textRange.startOffset, candidate.declaration.textRange.endOffset)
        val binding = valueBinding(target.name.orEmpty(), target.typeReference?.text, null, range)
        val calls: List<ScriptCall> = listOf(binding) + reader.readStatements(statements, nested = false).calls
        return PluginClassOutline(candidate.qualifiedName, calls, range, inliner.notInlined)
    }
}
