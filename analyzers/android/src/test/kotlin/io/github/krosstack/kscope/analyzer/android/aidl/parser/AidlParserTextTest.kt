package io.github.krosstack.kscope.analyzer.android.aidl.parser

import io.github.krosstack.kscope.analyzer.android.aidl.AidlSamples
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class AidlParserTextTest {
    @Test
    fun `enums, unions and nested types are skipped and reported`() {
        val file = parsed(AidlSamples.everything)
        val skipped = file.skipped.map { it.keyword to it.name.text }
        assertEquals(listOf("enum" to "Mode", "parcelable" to "Nested"), skipped)
        assertEquals(listOf("IEverything"), file.declarations.map { it.name.text })
        assertEquals(
            listOf("union" to "Choice"),
            parsed("union Choice { int a; String b; }\n").skipped.map { it.keyword to it.name.text },
        )
    }

    @Test
    fun `windows line ends and tabs give the columns an editor shows`() {
        val declaration = interfaceOf("package a.b;\r\n\r\n\tinterface I {\r\n\t\tvoid f();\r\n\t}\r\n")
        assertEquals(TextRange(3, 12, 3, 13), declaration.name.range)
        assertEquals(TextRange(4, 8, 4, 9), declaration.methods.single().name.range)
    }

    @Test
    fun `a byte order mark is not part of the first line`() {
        val declaration = interfaceOf("﻿package a;\ninterface I {}\n")
        assertEquals(TextRange(2, 11, 2, 12), declaration.name.range)
    }

    @Test
    fun `an empty file and a file of comments have no declarations`() {
        val nothing = AidlFile(null, emptyList(), emptyList(), emptyList())
        assertEquals(nothing, parsed(""))
        assertEquals(nothing, parsed("// nothing\n/* here */\n"))
    }
}
