package io.github.krosstack.kscope.analyzer.android.aidl.parser

import io.github.krosstack.kscope.model.TextRange
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal fun parsed(text: String): AidlFile =
    assertIs<AidlParseResult.Parsed>(AidlParser.parse(text), "the text should parse").file

internal fun interfaceOf(text: String): AidlInterface = parsed(text).declarations.single() as AidlInterface

internal fun assertProblem(text: String, message: String, range: TextRange) {
    val result = assertIs<AidlParseResult.Malformed>(AidlParser.parse(text), "the text should not parse")
    assertEquals(AidlProblem(message, range), result.problem)
}
