package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.RevisionProducer
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.store
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RevisionProducerTest {
    private val producer = RevisionProducer("history")

    private fun commit(workspace: TestWorkspace, message: String) {
        TestGit.output(workspace.root, "add", "-A")
        TestGit.output(workspace.root, "commit", "-q", "-m", message)
    }

    private fun withRepository(check: (TestWorkspace, ScanEngine) -> Unit) {
        assumeTrue(TestGit.available())
        TestWorkspace("plain").use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            commit(workspace, "initial")
            workspace.engine(coreRegistry(producer)).use { check(workspace, it) }
        }
    }

    @Test
    fun `the producer gets one repository unit that holds every artifact whatever its type`() =
        withRepository { workspace, engine ->
            engine.update()
            assertEquals(1, producer.units.size)
            assertEquals(engine.artifactPaths(), producer.units.single().toSet())
            val unit = engine.store.units().single { it.producer.id == "history" }
            assertEquals(engine.graph().structure().repositories.single().id.value, unit.owner.key)
            assertTrue(workspace.root.resolve("README.md").toFile().exists())
        }

    @Test
    fun `the request carries the repository with its head revision and the absolute root of the workspace`() =
        withRepository { workspace, engine ->
            engine.update()
            val checkout = assertNotNull(producer.checkouts.single())
            assertEquals(TestGit.output(workspace.root, "rev-parse", "HEAD"), checkout.repository.headRevision)
            assertEquals(workspace.root.toAbsolutePath().normalize(), checkout.root)
        }

    @Test
    fun `editing the content of a file does not run the producer again`() = withRepository { workspace, engine ->
        engine.update()
        workspace.write("src/Hello.kt", "class Edited\n")
        val report = engine.update()
        assertEquals(1, producer.units.size)
        assertTrue(report.unitsProduced > 0)
    }

    @Test
    fun `a new commit runs the producer again`() = withRepository { workspace, engine ->
        engine.update()
        workspace.write("src/Hello.kt", "class Edited\n")
        commit(workspace, "edit")
        engine.update()
        assertEquals(2, producer.units.size)
    }

    @Test
    fun `adding or deleting a file runs the producer again because the paths changed`() =
        withRepository { workspace, engine ->
            engine.update()
            workspace.write("src/Extra.kt", "class Extra\n")
            engine.update()
            workspace.delete("src/Extra.kt")
            engine.update()
            assertEquals(3, producer.units.size)
            assertTrue("src/Extra.kt" in producer.units[1])
            assertTrue("src/Extra.kt" !in producer.units[2])
        }

    @Test
    fun `without git the producer still runs and the revision is absent`() = TestWorkspace("plain").use { workspace ->
        workspace.engine(coreRegistry(producer)).use { engine ->
            engine.update()
            assertEquals(null, assertNotNull(producer.checkouts.single()).repository.headRevision)
            engine.update()
            assertEquals(1, producer.units.size)
        }
    }
}
