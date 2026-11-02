package io.github.krosstack.kscope.analyzer.android.aidl.parser

import io.github.krosstack.kscope.analyzer.android.aidl.AidlSamples
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AidlParserTest {
    @Test
    fun `a oneway interface is read with the package, the imports and the position of every name`() {
        val file = parsed(AidlSamples.syncCallback)
        assertEquals("sample.tasktide.sync.service", file.packageName)
        assertEquals(
            listOf(AidlImport("sample.tasktide.sync.service.SyncStatus", TextRange(3, 1, 3, 48))),
            file.imports,
        )
        val declaration = file.declarations.single() as AidlInterface
        assertTrue(declaration.isOneway)
        assertEquals(AidlName("ISyncCallback", TextRange(5, 18, 5, 31)), declaration.name)
        assertEquals(TextRange(5, 1, 11, 2), declaration.declaration)
        assertEquals(
            listOf("onSyncStarted", "onSyncFinished", "onSyncFailed"),
            declaration.methods.map { it.name.text },
        )
        assertEquals(TextRange(6, 10, 6, 23), declaration.methods[0].name.range)
        assertEquals(TextRange(6, 5, 6, 26), declaration.methods[0].declaration)
    }

    @Test
    fun `a parameter has its direction, type and name with positions`() {
        val method = interfaceOf(AidlSamples.syncCallback).methods[1]
        val parameter = method.parameters.single()
        assertEquals(AidlDirection.IN, parameter.direction)
        assertEquals("SyncStatus", parameter.type.name)
        assertEquals(TextRange(8, 28, 8, 38), parameter.type.range)
        assertEquals(AidlName("status", TextRange(8, 39, 8, 45)), parameter.name)
        assertEquals("void", method.returnType.name)
        assertFalse(method.isOneway)
    }

    @Test
    fun `generic and array types, annotations, constants and comments are read`() {
        val declaration = interfaceOf(AidlSamples.everything)
        assertEquals(listOf("LIMIT", "NAME", "BIG"), declaration.constants.map { it.name.text })
        assertEquals(listOf("1 << 4", "\"all; of it\"", "0x7fffffffL"), declaration.constants.map { it.value })
        val describe = declaration.methods[0]
        assertEquals("String", describe.returnType.name)
        assertEquals(
            listOf(AidlDirection.IN, AidlDirection.OUT, AidlDirection.INOUT),
            describe.parameters.map { it.direction },
        )
        assertEquals(listOf("String[]", "int[]", "List<String>"), describe.parameters.map { it.type.text() })
    }

    @Test
    fun `methods keep their modifiers, return types and transaction numbers out of the way`() {
        val methods = interfaceOf(AidlSamples.everything).methods
        assertEquals(listOf("describe", "fire", "index", "withId"), methods.map { it.name.text })
        assertTrue(methods[1].isOneway)
        assertEquals("Map<String, Foo>", methods[2].returnType.text())
        assertEquals(1, methods[2].parameters.last().type.arrayDimensions)
    }

    @Test
    fun `a file without a package keeps its interface`() {
        val file = parsed("interface I { void f(); }\n")
        assertNull(file.packageName)
        assertEquals(listOf("f"), (file.declarations.single() as AidlInterface).methods.map { it.name.text })
    }
}
