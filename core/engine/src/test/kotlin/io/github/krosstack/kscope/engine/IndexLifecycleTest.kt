package io.github.krosstack.kscope.engine

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IndexLifecycleTest {
    @Test
    fun `the graph of a workspace that was never scanned is empty`() = TestWorkspace("plain").use { workspace ->
        workspace.engine().use { engine ->
            assertTrue(engine.graph().artifacts().isEmpty())
            assertTrue(engine.graph().structure().repositories.isEmpty())
        }
    }

    @Test
    fun `an index in another schema version is discarded and rebuilt`() = TestWorkspace("plain").use { workspace ->
        workspace.engine().use { it.update() }
        DriverManager.getConnection("jdbc:sqlite:${workspace.indexDirectory.resolve("index.db")}").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA user_version = 999") }
        }
        workspace.engine().use { engine ->
            val report = engine.update()
            assertTrue(report.diagnostics.any { it.code.value == "index.rebuilt" })
            assertEquals(5, report.artifacts)
            assertEquals(1, report.unitsProduced)
        }
    }

    @Test
    fun `a rebuild produces every unit again and keeps the repository identity`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine().use { engine ->
                engine.update()
                val repository = engine.graph().structure().repositories.single().id
                val report = engine.rebuild()
                assertEquals(2, report.unitsProduced)
                assertEquals(0, report.unitsSkipped)
                assertEquals(repository, engine.graph().structure().repositories.single().id)
            }
        }

    @Test
    fun `a rebuild also drops the synthetic source cache`() = TestWorkspace("gradle-multi").use { workspace ->
        val synthesizer = RecordingSynthesizer("synthesizer")
        workspace.engine(coreRegistry(synthesizer)).use { engine ->
            engine.update()
            assertTrue(Files.isRegularFile(workspace.indexDirectory.resolve("synthetic-sources.json")))
            engine.rebuild()
            assertEquals(2, synthesizer.synthesizeCalls)
        }
    }

    @Test
    fun `verification passes after a clean incremental update`() = TestWorkspace("gradle-multi").use { workspace ->
        workspace.engine().use { engine ->
            val verify = assertNotNull(engine.update(verify = true).verify)
            assertTrue(verify.matches)
            assertEquals(0, verify.differingUnits)
            assertTrue(verify.differences.isEmpty())
        }
    }

    @Test
    fun `verification lists the units whose stored content differs from a rebuild`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine().use { engine ->
                engine.update()
                val discovery = engine.store.batches().single { it.producer.id == "kscope.discovery" }
                engine.store.replace(discovery.copy(facts = discovery.facts.dropLast(1)))
                val verify = assertNotNull(engine.update(verify = true).verify)
                assertFalse(verify.matches)
                assertEquals(1, verify.differingUnits)
                assertEquals("kscope.discovery", verify.differences.single().producer.id)
                assertEquals(DifferenceKind.DIFFERENT_CONTENT, verify.differences.single().kind)
            }
        }
}
