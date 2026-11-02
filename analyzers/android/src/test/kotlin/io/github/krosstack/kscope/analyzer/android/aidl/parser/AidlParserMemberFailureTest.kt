package io.github.krosstack.kscope.analyzer.android.aidl.parser

import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertIs

class AidlParserMemberFailureTest {
    @Test
    fun `a parameter without a name is reported at the closing parenthesis`() {
        assertProblem(
            "interface I { void f(int); }\n",
            "Expected a parameter name but found ')'",
            TextRange(1, 25, 1, 26),
        )
    }

    @Test
    fun `a constant without a value is reported`() {
        assertProblem(
            "interface I { const int X = ; }\n",
            "Expected a value but found ';'",
            TextRange(1, 29, 1, 30),
        )
    }

    @Test
    fun `a package without a name is reported`() {
        assertProblem("package ;\n", "Expected a package name but found ';'", TextRange(1, 9, 1, 10))
    }

    @Test
    fun `an unclosed annotation is reported at the end of the file`() {
        assertProblem(
            "@Anno(\ninterface I {}\n",
            "Expected ')' but found the end of the file",
            TextRange(3, 1, 3, 1),
        )
    }

    @Test
    fun `garbage never throws`() {
        val inputs = listOf(
            "}}}", "interface", "interface I", "@", "<<<>>>", "\"", "/*", "parcelable {", "interface I { void }",
        )
        inputs.forEach { assertIs<AidlParseResult.Malformed>(AidlParser.parse(it), it) }
    }
}
