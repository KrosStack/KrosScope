package io.github.krosstack.kscope.analyzer.kotlin.worker.plan

import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaTokenType
import com.intellij.psi.tree.IElementType

/**
 * The qualified names of the top-level types of a Java source, read from its tokens the way the compiler indexes a
 * Java source file: the package statement, then the name after each type keyword outside braces and parentheses.
 */
internal class JavaTypeNames(text: String) {
    private val lexer = JavaLexer(LanguageLevel.HIGHEST).apply { start(text) }
    private val packageName = StringBuilder()
    private val names = linkedSetOf<String>()
    private var depth = 0
    private var inPackage = false
    private var declaring = false

    fun read(): Set<String> {
        while (lexer.tokenType != null) {
            accept(checkNotNull(lexer.tokenType))
            lexer.advance()
        }
        return names
    }

    private fun accept(type: IElementType) {
        when {
            type in OPENING -> depth++
            type in CLOSING -> depth--
            depth != 0 -> Unit
            inPackage -> readPackage(type)
            type == JavaTokenType.PACKAGE_KEYWORD -> inPackage = true
            declaring && type == JavaTokenType.IDENTIFIER -> declare(lexer.tokenText)
            type in TYPE_KEYWORDS || isRecord(type) -> declaring = true
        }
    }

    private fun readPackage(type: IElementType) {
        when (type) {
            JavaTokenType.SEMICOLON -> inPackage = false
            JavaTokenType.IDENTIFIER, JavaTokenType.DOT -> packageName.append(lexer.tokenText)
        }
    }

    private fun declare(name: String) {
        names += if (packageName.isEmpty()) name else "$packageName.$name"
        declaring = false
    }

    private fun isRecord(type: IElementType) = type == JavaTokenType.IDENTIFIER && lexer.tokenText == RECORD

    private companion object {
        const val RECORD = "record"
        val TYPE_KEYWORDS =
            setOf(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD, JavaTokenType.ENUM_KEYWORD)
        val OPENING = setOf(JavaTokenType.LBRACE, JavaTokenType.LPARENTH)
        val CLOSING = setOf(JavaTokenType.RBRACE, JavaTokenType.RPARENTH)
    }
}
