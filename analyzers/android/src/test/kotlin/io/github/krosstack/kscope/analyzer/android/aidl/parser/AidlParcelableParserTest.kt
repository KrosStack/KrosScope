package io.github.krosstack.kscope.analyzer.android.aidl.parser

import io.github.krosstack.kscope.analyzer.android.aidl.AidlSamples
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AidlParcelableParserTest {
    @Test
    fun `an unstructured parcelable only names a class`() {
        val parcelable = parsed(AidlSamples.syncStatus).declarations.single() as AidlParcelable
        assertEquals(AidlName("SyncStatus", TextRange(3, 12, 3, 22)), parcelable.name)
        assertFalse(parcelable.isStructured)
        assertNull(parcelable.qualifier)
        assertEquals(TextRange(3, 1, 3, 23), parcelable.declaration)
    }

    @Test
    fun `an unstructured parcelable may carry a package and native headers`() {
        val file = parsed("parcelable android.os.Bundle;\nparcelable Foo cpp_header \"foo/Foo.h\";\n")
        val declarations = file.declarations.map { it as AidlParcelable }
        assertEquals("android.os", declarations[0].qualifier)
        assertEquals("Bundle", declarations[0].name.text)
        assertEquals("Foo", declarations[1].name.text)
        assertFalse(declarations[1].isStructured)
    }

    @Test
    fun `a structured parcelable has fields with types and defaults, and constants`() {
        val parcelable = parsed(AidlSamples.report).declarations.single() as AidlParcelable
        assertTrue(parcelable.isStructured)
        assertEquals(listOf("count", "title", "tags", "extras", "history"), parcelable.fields.map { it.name.text })
        assertEquals("\"none\"", parcelable.fields[1].defaultValue)
        assertNull(parcelable.fields[0].defaultValue)
        assertEquals("List<String>", parcelable.fields[2].type.text())
        assertEquals("sample.tasktide.sync.service.SyncStatus[]", parcelable.fields[4].type.text())
        val constant = parcelable.constants.single()
        assertEquals(Triple("STATE_OPEN", "int", "1"), Triple(constant.name.text, constant.type.name, constant.value))
    }
}
