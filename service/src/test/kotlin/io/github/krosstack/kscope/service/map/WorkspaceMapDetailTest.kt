package io.github.krosstack.kscope.service.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorkspaceMapDetailTest {
    private val map = sampleMap()

    @Test
    fun `a generated symbol without a declaration sits in the file of its source of truth`() {
        val detail = assertNotNull(map.detail(fileId(B_FILE)))

        assertEquals(3, map.node(fileId(B_FILE)).symbols)
        assertEquals(listOf("io.x.model.B", "io.x.model.B2", "io.x.model.G"), detail.symbols.map { it.id }.sorted())
        assertEquals(true, detail.symbols.first { it.id == "io.x.model.G" }.generated)
    }

    @Test
    fun `the detail of a file lists its symbols by line and the relationships around it`() {
        val detail = assertNotNull(map.detail(fileId(A_FILE)))

        assertEquals(listOf("io.x.model.A2", "io.x.model.A"), detail.symbols.map { it.id })
        assertEquals(listOf(1, 3), detail.symbols.map { it.line })
        assertEquals(7, detail.outgoing.size)
        assertEquals(listOf("unresolved", "not-analysed"), detail.open.map { it.state })
        assertEquals(emptyList(), detail.incoming)
    }

    @Test
    fun `the detail of a file lists what reaches it with the labels of both ends`() {
        val detail = assertNotNull(map.detail(fileId(B_FILE)))
        val link = detail.incoming.first()

        assertEquals(3, detail.incoming.size)
        assertEquals("calls", link.kind)
        assertEquals("exact", link.certainty)
        assertEquals("io.x.model.A", link.fromLabel)
        assertEquals("io.x.model.A", link.fromSymbol)
        assertEquals(map.position(fileId(A_FILE)), link.fromNode)
        assertEquals(map.position(fileId(B_FILE)), link.toNode)
    }

    @Test
    fun `a relationship to a symbol outside the tree keeps its end unplaced`() {
        val ghost = assertNotNull(map.detail(fileId(A_FILE))).outgoing.last()

        assertEquals(-1, ghost.toNode)
        assertEquals("ghost", ghost.toSymbol)
    }

    @Test
    fun `an unknown node has no detail`() {
        assertNull(map.detail("f:repo//nothing.kt"))
    }
}
