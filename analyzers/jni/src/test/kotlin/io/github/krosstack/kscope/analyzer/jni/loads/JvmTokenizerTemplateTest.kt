package io.github.krosstack.kscope.analyzer.jni.loads

import kotlin.test.Test
import kotlin.test.assertEquals

private const val RAW = "\"\"\""
private const val OPEN = "\${"

class JvmTokenizerTemplateTest {
    private fun kotlin(text: String) = JvmTokenizer(text, JvmDialect.KOTLIN).tokens().map { it.text }

    private fun loadAfterTemplate(expression: String): Pair<List<String>, List<String>> {
        val template = "\"" + OPEN + expression + "}\""
        val tokens = kotlin("val s = " + template + "\nSystem.loadLibrary(\"foo\")")
        return tokens to listOf("val", "s", "=", template, "System", ".", "loadLibrary", "(", "\"foo\"", ")")
    }

    @Test
    fun `a brace in a comment inside a template expression does not keep the template open`() {
        listOf(" /* { */ 1 ", " /* /* { */ */ 1 ", "\n  1 // {\n", " /* } */ 1 ").forEach {
            val (actual, expected) = loadAfterTemplate(it)
            assertEquals(expected, actual, it)
        }
    }

    @Test
    fun `a brace in a string or character inside a template expression does not count`() {
        val expressions = listOf(
            " \"}\" ",
            " " + RAW + "}{" + RAW + " ",
            " \"a" + OPEN + "'}'}\" ",
            " '{' ",
            " '}' ",
        )
        expressions.forEach {
            val (actual, expected) = loadAfterTemplate(it)
            assertEquals(expected, actual, it)
        }
    }
}
