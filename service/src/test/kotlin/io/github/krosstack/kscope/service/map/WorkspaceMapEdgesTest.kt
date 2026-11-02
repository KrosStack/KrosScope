package io.github.krosstack.kscope.service.map

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceMapEdgesTest {
    private val map = sampleMap()

    @Test
    fun `resolved relationships are counted per pair of nodes kind and certainty`() {
        val overview = map.overview
        val edges = overview.edges.chunked(EDGE_STRIDE).map { edge ->
            listOf(
                overview.nodes[edge[0]].label,
                overview.nodes[edge[1]].label,
                overview.kinds[edge[2]],
                overview.certainties[edge[3]],
                edge[4],
            )
        }

        assertEquals(
            listOf(
                listOf("A.kt", "B.kt", "calls", "exact", 2),
                listOf("A.kt", "B.kt", "imports", "heuristic", 1),
                listOf("A.kt", "annotations", "type-uses", "exact", 1),
                listOf("A.kt", "npm:left-pad", "references", "probable", 1),
            ),
            edges,
        )
    }

    @Test
    fun `a relationship with an end outside the tree is counted as unplaced and not drawn`() {
        assertEquals(1, map.overview.unplaced)
    }

    @Test
    fun `a relationship inside one file and a contains relationship are not drawn`() {
        val drawn = map.overview.edges.chunked(EDGE_STRIDE).sumOf { it[4] }

        assertEquals(5, drawn)
    }

    @Test
    fun `two builds of the same facts are equal`() {
        val again = sampleMap()

        assertEquals(map.overview, again.overview)
        assertEquals(map.detail(fileId(A_FILE)), again.detail(fileId(A_FILE)))
    }

    private companion object {
        const val EDGE_STRIDE = 5
    }
}
