package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.settings.SettingsWorkspace
import io.github.krosstack.kscope.service.settings.canonicalDump
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyntaxLevelScanTest {
    private fun SettingsWorkspace.at(level: ScanLevel) = OpenOptions(indexDirectory = index, level = level)

    private fun <T> SettingsWorkspace.session(level: ScanLevel? = null, block: (KrosScope) -> T): T =
        open(level?.let { at(it) } ?: options(), block = block)

    private fun SettingsWorkspace.producers(): Set<String> =
        session { scope -> (scope.engine.graph() as GraphStore).units().map { it.producer.id }.toSet() }

    private fun cleanDump(level: ScanLevel, prepare: SettingsWorkspace.() -> Unit = {}): String =
        SettingsWorkspace().use { clean ->
            clean.prepare()
            clean.session(level) {
                it.scan()
                it.canonicalDump()
            }
        }

    @Test
    fun `a syntax scan says so, records the level and reads it back`() {
        SettingsWorkspace().use { workspace ->
            workspace.session(ScanLevel.SYNTAX) {
                assertNull(it.indexLevel())
                assertEquals("syntax", it.scan().indexLevel)
                assertEquals(ScanLevel.SYNTAX, it.indexLevel())
                assertEquals("syntax", it.info().indexLevel)
            }
            assertTrue("kscope.level" in workspace.producers())
        }
    }

    @Test
    fun `a syntax scan has declarations, no compiler evidence and a note per source set`() {
        SettingsWorkspace().use { workspace ->
            workspace.session(ScanLevel.SYNTAX) {
                val result = it.scan()
                val store = it.engine.graph() as GraphStore
                assertTrue(store.batches().flatMap { batch -> batch.facts }.isNotEmpty())
                val compiler = RelationshipQuery(evidenceClass = EvidenceClass.COMPILER)
                assertEquals(emptyList(), it.graph().relationships(compiler))
                assertTrue(result.diagnostics.entries.any { d -> d.code == "kotlin.references.not-analysed-at-syntax" })
            }
            val producers = workspace.producers()
            assertTrue("kscope.analyzer.kotlin.syntax" in producers)
            assertFalse("kscope.analyzer.kotlin" in producers)
        }
    }

    @Test
    fun `a workspace without Kotlin records the syntax level too`() {
        SettingsWorkspace().use { workspace ->
            workspace.delete("lib/src/main/kotlin/Lib.kt")
            workspace.session(ScanLevel.SYNTAX) {
                it.scan()
                assertEquals(ScanLevel.SYNTAX, it.indexLevel())
            }
        }
    }

    @Test
    fun `deepening to resolved removes exactly the syntax units and equals a clean resolved scan`() {
        SettingsWorkspace().use { workspace ->
            workspace.session(ScanLevel.SYNTAX) { it.scan() }
            val shallow = workspace.producers()

            val result = workspace.session { it.scan() }

            val deep = workspace.producers()
            assertEquals("resolved", result.indexLevel)
            assertEquals(setOf("kscope.level", "kscope.analyzer.kotlin.syntax"), shallow - deep)
            assertEquals(setOf("kscope.analyzer.kotlin"), deep - shallow)
            assertEquals(cleanDump(ScanLevel.RESOLVED), workspace.session { it.canonicalDump() })
        }
    }

    @Test
    fun `going back to syntax needs a rebuild and then equals a clean syntax scan`() {
        SettingsWorkspace().use { workspace ->
            workspace.session { it.scan() }
            val before = workspace.session { it.canonicalDump() }

            assertFailsWith<LevelRefusedException> { workspace.session(ScanLevel.SYNTAX) { it.scan() } }
            assertEquals(before, workspace.session { it.canonicalDump() })
            workspace.session(ScanLevel.SYNTAX) { it.scan(rebuild = true) }

            assertEquals(cleanDump(ScanLevel.SYNTAX), workspace.session { it.canonicalDump() })
            workspace.session { assertEquals(ScanLevel.SYNTAX, it.indexLevel()) }
        }
    }

    @Test
    fun `a plain scan of a syntax index deepens it to resolved`() {
        SettingsWorkspace().use { workspace ->
            workspace.session(ScanLevel.SYNTAX) { it.scan() }

            workspace.session { assertEquals("resolved", it.scan().indexLevel) }
        }
    }

    @Test
    fun `an incremental syntax scan after an edit equals a clean one and verifies`() {
        val edited = "class Lib\nclass Added\n"
        SettingsWorkspace().use { workspace ->
            workspace.session(ScanLevel.SYNTAX) { it.scan() }
            workspace.write("lib/src/main/kotlin/Lib.kt", edited)

            val result = workspace.session(ScanLevel.SYNTAX) { it.scan(verify = true) }

            assertEquals(true, result.verification?.matches)
            val clean = cleanDump(ScanLevel.SYNTAX) { write("lib/src/main/kotlin/Lib.kt", edited) }
            assertEquals(clean, workspace.session { it.canonicalDump() })
        }
    }
}
