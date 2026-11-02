package io.github.krosstack.kscope.analyzer.android.aidl.parser

import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test

class AidlParserFailureTest {
    @Test
    fun `a method without a semicolon is reported at the token that follows it`() {
        assertProblem(
            "package a;\ninterface I { void f() }\n",
            "Expected ';' but found '}'",
            TextRange(2, 24, 2, 25),
        )
    }

    @Test
    fun `an unterminated comment is reported where it starts`() {
        assertProblem("package a;\n/* never closed\ninterface I {}\n", "Unterminated comment", TextRange(2, 1, 2, 3))
    }

    @Test
    fun `an unterminated string is reported with the extent of its line`() {
        assertProblem(
            "interface I {\n    const String S = \"abc;\n}\n",
            "Unterminated string",
            TextRange(2, 22, 2, 27),
        )
    }

    @Test
    fun `a missing closing brace is reported at the end of the file`() {
        assertProblem(
            "interface I {\n    void f();\n",
            "Expected '}' but found the end of the file",
            TextRange(3, 1, 3, 1),
        )
    }

    @Test
    fun `an unknown declaration is reported with the word`() {
        assertProblem(
            "foo bar;\n",
            "Expected 'interface', 'parcelable', 'enum' or 'union' but found 'foo'",
            TextRange(1, 1, 1, 4),
        )
    }

    @Test
    fun `oneway belongs to interfaces and methods only`() {
        assertProblem(
            "oneway parcelable Foo;\n",
            "Expected 'interface', 'parcelable', 'enum' or 'union' but found 'parcelable'",
            TextRange(1, 8, 1, 18),
        )
    }
}
