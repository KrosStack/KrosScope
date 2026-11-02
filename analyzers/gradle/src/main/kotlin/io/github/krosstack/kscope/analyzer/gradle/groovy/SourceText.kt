package io.github.krosstack.kscope.analyzer.gradle.groovy

import io.github.krosstack.kscope.analyzer.gradle.script.LineIndex
import io.github.krosstack.kscope.model.TextRange
import org.codehaus.groovy.ast.ASTNode

/** Maps the 1-based line and column positions of syntax tree nodes to the text of the script. */
internal class SourceText(private val text: String) {
    private val lines = LineIndex(text)

    fun range(node: ASTNode): TextRange? {
        val isKnown = node.lineNumber >= 1 && node.lastLineNumber >= 1
        return TextRange(node.lineNumber, node.columnNumber, node.lastLineNumber, node.lastColumnNumber)
            .takeIf { isKnown }
    }

    fun slice(node: ASTNode): String? = slice(node, node)

    /** The text from the start of [first] to the end of [last]. */
    fun slice(first: ASTNode, last: ASTNode): String? {
        val start = range(first)?.let { lines.offsetOf(it.startLine, it.startColumn) }
        val end = range(last)?.let { lines.offsetOf(it.endLine, it.endColumn) }
        return if (start != null && end != null && start <= end) text.substring(start, end) else null
    }
}
