package io.github.krosstack.kscope.analyzer.jni.natives

import io.github.krosstack.kscope.analyzer.jni.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.jni.lexing.TokenKind
import io.github.krosstack.kscope.analyzer.jni.lexing.afterBalanced
import io.github.krosstack.kscope.model.TextRange

internal enum class NativeRole(val value: String) {
    DEFINITION("definition"),
    DECLARATION("declaration"),
}

/** A function named like a JNI function: where its name is, and where its definition or declaration is. */
internal data class NativeFunction(
    val name: String,
    val role: NativeRole,
    val signature: String,
    val nameRange: TextRange,
    val declaration: TextRange,
)

private const val JNI_PREFIX = "Java_"
private val notATypeBeforeName = setOf("return", "else", "case", "goto", "sizeof", "typedef", "new", "delete", "throw")
private val lineComments = Regex("//[^\\n]*")
private val blockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
private val whiteSpace = Regex("\\s+")

/**
 * Finds the functions named `Java_*` that a C or C++ file defines or declares: a name followed by a parameter list,
 * behind a return type, at file scope or inside `extern "C"` and `namespace` blocks. A call inside a function body is
 * not one, and neither is anything in a comment or a string, because the tokens come from [CTokenizer]. This is a
 * lexical match, not a C parser: macros that build such names are not seen.
 */
internal class NativeFunctionScanner(private val text: String) : FunctionScanner {
    private val tokens = CTokenizer(text).tokens()

    override fun scan(): List<NativeFunction> {
        val functions = mutableListOf<NativeFunction>()
        var index = 0
        while (index < tokens.size) {
            val function = if (isFunctionName(index)) functionAt(index) else null
            index = when {
                function != null -> function.second.also { functions += function.first }

                tokens[index].isPunctuation("{") && !opensTransparentBlock(index) ->
                    tokens.afterBalanced(index, "{", "}")

                else -> index + 1
            }
        }
        return functions
    }

    private fun isFunctionName(index: Int): Boolean {
        val token = tokens[index]
        val before = tokens.getOrNull(index - 1) ?: return false
        val startsLikeJni = token.kind == TokenKind.IDENTIFIER && token.text.startsWith(JNI_PREFIX)
        val hasType = (before.kind == TokenKind.IDENTIFIER && before.text !in notATypeBeforeName) ||
            before.isPunctuation("*") || before.isPunctuation("&")
        return startsLikeJni && hasType && tokens.getOrNull(index + 1)?.isPunctuation("(") == true
    }

    // The function at [nameIndex] and the index after it; null when what follows the parameters is no body or `;`.
    private fun functionAt(nameIndex: Int): Pair<NativeFunction, Int>? {
        val closing = tokens.afterBalanced(nameIndex + 1, "(", ")") - 1
        val next = afterAttributes(closing + 1)
        val terminator = tokens.getOrNull(next)
        return when {
            terminator == null -> null

            terminator.isPunctuation("{") ->
                functionOf(nameIndex, closing, tokens.afterBalanced(next, "{", "}") - 1)

            terminator.isPunctuation(";") -> functionOf(nameIndex, closing, next)

            else -> null
        }
    }

    // Identifiers after the parameters, such as `noexcept` or `__attribute__((unused))`, belong to the function.
    private fun afterAttributes(start: Int): Int {
        var next = start
        while (tokens.getOrNull(next)?.kind == TokenKind.IDENTIFIER) {
            next++
            if (tokens.getOrNull(next)?.isPunctuation("(") == true) next = tokens.afterBalanced(next, "(", ")")
        }
        return next
    }

    private fun functionOf(nameIndex: Int, closing: Int, last: Int): Pair<NativeFunction, Int> {
        val first = startOfDeclaration(nameIndex)
        val role = if (tokens[last].isPunctuation("}")) NativeRole.DEFINITION else NativeRole.DECLARATION
        val start = tokens[first].range
        val end = tokens[last].range
        val range = TextRange(start.startLine, start.startColumn, end.endLine, end.endColumn)
        val name = tokens[nameIndex]
        return NativeFunction(name.text, role, headerText(first, closing), name.range, range) to last + 1
    }

    private fun startOfDeclaration(nameIndex: Int): Int {
        var first = nameIndex
        while (first > 0 && !isBoundary(tokens[first - 1])) first--
        val hasLinkage = tokens[first].isIdentifier("extern") && tokens.getOrNull(first + 1)?.kind == TokenKind.STRING
        return if (hasLinkage && first + 2 < nameIndex) first + 2 else first
    }

    private fun isBoundary(token: SourceToken) =
        token.isPunctuation(";") || token.isPunctuation("{") || token.isPunctuation("}")

    private fun headerText(first: Int, last: Int): String {
        val raw = text.substring(tokens[first].start, tokens[last].end)
        return raw.replace(blockComments, " ").replace(lineComments, " ").replace(whiteSpace, " ").trim()
    }

    private fun opensTransparentBlock(brace: Int): Boolean {
        val isExternBlock = tokens.getOrNull(brace - 1)?.kind == TokenKind.STRING &&
            tokens.getOrNull(brace - 2)?.isIdentifier("extern") == true
        return isExternBlock || isNamespaceHeader(brace - 1)
    }

    private fun isNamespaceHeader(last: Int): Boolean {
        var index = last
        while (index >= 0 && (tokens[index].kind == TokenKind.IDENTIFIER || tokens[index].isPunctuation(":"))) {
            if (tokens[index].isIdentifier("namespace")) return true
            index--
        }
        return false
    }
}
