package io.github.krosstack.kscope.analyzer.jni.natives

import io.github.krosstack.kscope.analyzer.jni.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.jni.lexing.TokenKind
import io.github.krosstack.kscope.analyzer.jni.lexing.afterBalanced
import io.github.krosstack.kscope.model.TextRange

private const val JNI_PREFIX = "Java_"
private val qualifiers = setOf("pub", "unsafe", "extern", "const", "async", "safe", "default")

/**
 * Finds the functions named `Java_*` that a Rust file defines: `fn` items with an `extern` qualifier, such as
 * `pub extern "system" fn Java_p_C_f<'local>(...)`, at file level or inside `mod` blocks. A function inside another
 * function, an `impl` block, a foreign `extern` block or a macro is not one, and neither is anything in a comment or a
 * string, because the tokens come from [RustTokenizer]. Attributes such as `#[no_mangle]` are not required, since the
 * match of the name to a Kotlin declaration is what the linker decides. This is a lexical match, not a Rust parser.
 */
internal class RustFunctionScanner(text: String) : FunctionScanner {
    private val tokens = RustTokenizer(text).tokens()
    private val macros = RustMacroInvocations(tokens)

    override fun scan(): List<NativeFunction> {
        val functions = mutableListOf<NativeFunction>()
        var index = 0
        while (index < tokens.size) {
            val function = if (isFunctionName(index)) functionAt(index) else null
            val afterMacro = macros.endOfInvocationAt(index)
            index = when {
                function != null -> function.second.also { functions += function.first }
                afterMacro != null -> afterMacro
                tokens[index].isPunctuation("{") && !opensModule(index) -> tokens.afterBalanced(index, "{", "}")
                else -> index + 1
            }
        }
        return functions
    }

    private fun isFunctionName(index: Int): Boolean {
        val token = tokens[index]
        val isNamedLikeJni = token.kind == TokenKind.IDENTIFIER && token.text.startsWith(JNI_PREFIX)
        val after = tokens.getOrNull(index + 1)
        val hasParameters = after?.isPunctuation("(") == true || after?.isPunctuation("<") == true
        return isNamedLikeJni && hasParameters && tokens.getOrNull(index - 1)?.isIdentifier("fn") == true
    }

    private fun functionAt(nameIndex: Int): Pair<NativeFunction, Int>? {
        val first = startOfDeclaration(nameIndex - 1)
        val body = bodyAfter(nameIndex)
        val isExtern = (first until nameIndex).any { tokens[it].isIdentifier("extern") }
        return if (isExtern && body != null) functionOf(first, nameIndex, body) else null
    }

    private fun bodyAfter(nameIndex: Int): Int? {
        val generics = nameIndex + 1
        val parameters = if (tokens[generics].isPunctuation("<")) tokens.afterBalanced(generics, "<", ">") else generics
        val afterParameters = tokens.afterBalanced(parameters, "(", ")")
        val end = (afterParameters until tokens.size).firstOrNull {
            tokens[it].isPunctuation("{") || tokens[it].isPunctuation(";")
        }
        return end?.takeIf { tokens[it].isPunctuation("{") }
    }

    private fun startOfDeclaration(fnIndex: Int): Int {
        var first = fnIndex
        while (first > 0 && isQualifier(tokens[first - 1])) first--
        return first
    }

    private fun isQualifier(token: SourceToken) =
        token.kind == TokenKind.STRING || (token.kind == TokenKind.IDENTIFIER && token.text in qualifiers)

    private fun functionOf(first: Int, nameIndex: Int, body: Int): Pair<NativeFunction, Int> {
        val last = tokens.afterBalanced(body, "{", "}") - 1
        val start = tokens[first].range
        val end = tokens[last].range
        val range = TextRange(start.startLine, start.startColumn, end.endLine, end.endColumn)
        val name = tokens[nameIndex]
        val signature = signatureOf(first, body - 1)
        return NativeFunction(name.text, NativeRole.DEFINITION, signature, name.range, range) to last + 1
    }

    private fun signatureOf(first: Int, last: Int): String = (first..last).joinToString("") { index ->
        val previous = tokens.getOrNull(index - 1)?.takeIf { index > first }
        (if (previous != null && isSpaced(previous, tokens[index])) " " else "") + tokens[index].text
    }

    private fun isSpaced(previous: SourceToken, next: SourceToken) =
        previous.end < next.start && !previous.isPunctuation("(") && !next.isPunctuation(")")

    private fun opensModule(brace: Int): Boolean {
        val isNamed = tokens.getOrNull(brace - 1)?.kind == TokenKind.IDENTIFIER
        return isNamed && tokens.getOrNull(brace - 2)?.isIdentifier("mod") == true
    }
}
