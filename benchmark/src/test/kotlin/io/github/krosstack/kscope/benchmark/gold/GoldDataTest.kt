package io.github.krosstack.kscope.benchmark.gold

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoldDataTest {
    private val gold = GoldLoader.load(Path.of(checkNotNull(System.getProperty("kscope.gold"))))
    private val fixture = Path.of(checkNotNull(System.getProperty("kscope.fixture")))

    @Test
    fun `the relationship counts of the metadata agree with the relationships`() {
        val counted = gold.relationships.relationships.groupingBy { it.relationshipClass }.eachCount()
        assertEquals(gold.relationships.meta.counts, counted)
    }

    @Test
    fun `every closed-world file lists the declarations that were derived from its source`() {
        assertEquals(8, gold.closedWorld.size)
        gold.closedWorld.forEach { file ->
            assertTrue(file.declarations.isNotEmpty(), "${file.file} has no declarations")
            val kinds = file.declarations.map { it.kind }.toSet()
            assertTrue(kinds.all { it in DECLARATION_KINDS }, "${file.file} has the kinds $kinds")
        }
    }

    @Test
    fun `the tasks load and name relationships that exist`() {
        assertEquals(14, gold.tasks.size)
        val ids = gold.relationships.relationships.map { it.id }.toSet()
        assertTrue(gold.tasks.flatMap { it.relationships }.all { it in ids })
    }

    @Test
    fun `the gold data is consistent with the files of the reference workspace`() {
        assertEquals(emptyList(), GoldValidator(lineCounts()).problems(gold))
    }

    @Test
    fun `the validator reports what is inconsistent`() {
        val broken = brokenGold()
        val problems = GoldValidator(mapOf("a/Real.kt" to 10)).problems(broken)
        assertEquals(
            listOf(
                "T1: no such file a/Missing.kt",
                "T1: unknown relationship nope:001",
                "a/Real.kt: a line lies outside the file",
                "a/Real.kt: no declarations",
            ),
            problems,
        )
    }

    private fun brokenGold(): GoldSet {
        val real = GoldAddress(file = "a/Real.kt", fqName = "p.Real", line = 3)
        val edge = GoldEdge(line = 11, column = 1, text = "x", kind = "call", target = real)
        val file = GoldClosedWorldFile("w", "a/Real.kt", ":m", "main", "kotlin", "c", edges = listOf(edge))
        val task = GoldTask("T1", "t", "s", "c", mustEdit = listOf("a/Missing.kt"), relationships = listOf("nope:001"))
        val relationships = gold.relationships.copy(relationships = emptyList())
        return GoldSet(gold.buildModel, relationships, listOf(file), listOf(task))
    }

    private fun lineCounts(): Map<String, Int> = Files.walk(fixture).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.none { part -> part.toString() in SKIPPED } }
            .toList().associate { fixture.relativize(it).toString().replace('\\', '/') to linesOf(it) }
    }

    private fun linesOf(file: Path): Int {
        val bytes = Files.readAllBytes(file)
        val newlines = bytes.count { it == NEWLINE }
        return if (bytes.isNotEmpty() && bytes.last() != NEWLINE) newlines + 1 else newlines
    }

    private companion object {
        val DECLARATION_KINDS =
            setOf("class", "interface", "object", "enum-entry", "typealias", "function", "property", "constructor")
        val SKIPPED = setOf("build", ".gradle", ".kscope", ".git", ".idea", ".kotlin", ".cxx")
        const val NEWLINE = '\n'.code.toByte()
    }
}
