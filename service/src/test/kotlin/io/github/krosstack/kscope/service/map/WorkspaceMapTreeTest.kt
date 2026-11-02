package io.github.krosstack.kscope.service.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceMapTreeTest {
    private val map = sampleMap()
    private val nodes = map.overview.nodes

    @Test
    fun `the tree lists parents before children and siblings by label`() {
        val expected = listOf(
            "w:",
            "m:repo//(root files)",
            "f:repo//README.md",
            "m:repo//:core",
            "f:repo//core/build.gradle.kts",
            "s:repo//:core@main",
            "d:s:repo//:core@main/model",
            fileId(A_FILE),
            fileId(B_FILE),
            "d:s:repo//:core@main/util",
            fileId(C_FILE),
            "m:repo//:lib",
            "s:repo//:lib@main",
            fileId(Y_FILE),
            "d:s:repo//:lib@main/q/r",
            fileId(X_FILE),
            "L:",
            "g:org.jetbrains",
            "x:maven:org.jetbrains:annotations:24.0",
            "g:other",
            "x:npm:left-pad",
        )

        assertEquals(expected, nodes.map { it.id })
        nodes.forEachIndexed { position, node -> assertTrue(node.parent < position, node.id) }
        assertEquals(-1, nodes[0].parent)
    }

    @Test
    fun `a directory that holds only one directory is joined with it`() {
        val joined = map.node("d:s:repo//:lib@main/q/r")

        assertEquals("q/r", joined.label)
        assertEquals("directory", joined.type)
        assertEquals(map.position("s:repo//:lib@main"), joined.parent)
    }

    @Test
    fun `directories start below the directory that all files of a source set share`() {
        val set = map.position("s:repo//:core@main")

        assertEquals(listOf("model", "util"), nodes.filter { it.parent == set }.map { it.label })
    }

    @Test
    fun `a file shows its facts and the number of symbols declared in it`() {
        val file = map.node(fileId(A_FILE))

        assertEquals("A.kt", file.label)
        assertEquals(A_FILE, file.path)
        assertEquals(40, file.lines)
        assertEquals(2, file.symbols)
        assertEquals("kotlin.source", file.artifactType)
        assertEquals("source", file.category)
        assertEquals("0123456789ab", file.hash)
        assertEquals(7, file.commits)
        assertEquals(1, file.unresolved)
        assertEquals(1, file.notAnalysed)
    }

    @Test
    fun `modules carry their traits and the level and kinds are named`() {
        assertEquals(listOf("kotlin-jvm"), map.node("m:repo//:core").traits)
        assertEquals("resolved", map.overview.level)
        assertEquals("demo", map.overview.workspace)
        assertEquals(listOf("exact", "probable", "heuristic"), map.overview.certainties)
        assertTrue("contains" !in map.overview.kinds)
        assertEquals("references", map.overview.kinds.first())
    }

    @Test
    fun `libraries are grouped by Maven group and others go under other`() {
        assertEquals("libraries", map.node("L:").type)
        assertEquals("library-group", map.node("g:org.jetbrains").type)
        assertEquals("annotations", map.node("x:maven:org.jetbrains:annotations:24.0").label)
        assertEquals(map.position("g:other"), map.node("x:npm:left-pad").parent)
    }

    @Test
    fun `a workspace without a library edge has no libraries node`() {
        val facts = sampleFacts().copy(relationships = emptyList())

        assertEquals(null, WorkspaceMapBuilder(facts).build().positionOf("L:"))
    }

    @Test
    fun `a workspace without an index has no nodes`() {
        val empty = WorkspaceMap.empty("demo")

        assertEquals(emptyList(), empty.overview.nodes)
        assertEquals("demo", empty.overview.workspace)
        assertEquals(null, empty.detail("w:"))
    }
}
