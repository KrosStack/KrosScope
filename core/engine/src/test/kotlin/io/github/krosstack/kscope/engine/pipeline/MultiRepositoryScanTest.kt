package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.RevisionProducer
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.TwoRepositoryWorkspace
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MultiRepositoryScanTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `each repository is a fact with its own id and its directory`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            val repositories = engine.graph().structure().repositories.sortedBy { it.directory.value }
            assertEquals(listOf("application", "library"), repositories.map { it.directory.value })
            assertEquals(listOf("application", "library"), repositories.map { it.name })
            assertNotEquals(repositories.first().id, repositories.last().id)
            assertTrue(repositories.all { it.headRevision != null })
        }
    }

    @Test
    fun `build roots and modules are keyed by their own repository`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            val structure = engine.graph().structure()
            val repositories = structure.repositories.associateBy { it.directory.value }
            structure.buildRoots.forEach { assertTrue(it.id.value.startsWith(it.repository.value)) }
            structure.modules.forEach { module ->
                val root = structure.buildRoots.single { it.id == module.buildRoot }
                assertTrue(module.id.value.startsWith(root.id.value))
            }
            assertEquals(
                setOf(repositories.getValue("application").id, repositories.getValue("library").id),
                structure.buildRoots.map { it.repository }.toSet(),
            )
            assertEquals(listOf(":app", ":model"), structure.modules.map { it.path }.sorted())
        }
    }

    @Test
    fun `artifact paths are relative to their repository and files outside the checkouts are not indexed`() =
        TwoRepositoryWorkspace().use { workspace ->
            workspace.write("notes.txt", "outside\n")
            workspace.engine().use { engine ->
                val report = engine.update()
                val artifacts = engine.graph().artifacts()
                val settings = artifacts.filter { it.ref.path.value == "settings.gradle.kts" }
                assertEquals(2, settings.map { it.ref.repository }.distinct().size)
                assertTrue("model/src/main/kotlin/org/example/library/Record.kt" in engine.artifactPaths())
                assertTrue(artifacts.none { it.ref.path.value.startsWith("library/") })
                assertTrue("notes.txt" !in engine.artifactPaths())
                assertTrue(report.diagnostics.any { it.code.value == "workspace.files-outside-repositories" })
            }
        }

    @Test
    fun `source sets are assigned within the repository of the artifact`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            val structure = engine.graph().structure()
            val record = engine.graph().artifacts().single { it.ref.path.value.endsWith("Record.kt") }
            val sourceSet = structure.sourceSets.single { it.id == record.sourceSet }
            val module = structure.modules.single { it.id == sourceSet.module }
            assertEquals(":model", module.path)
            assertTrue(module.id.value.startsWith(record.ref.repository.value))
        }
    }

    @Test
    fun `a verified update reports no difference and a second update produces nothing`() =
        TwoRepositoryWorkspace().use { workspace ->
            workspace.engine().use { engine ->
                engine.update()
                val second = engine.update(verify = true)
                assertEquals(0, second.unitsProduced)
                assertTrue(second.verify?.matches == true, "${second.verify?.differences}")
            }
        }

    @Test
    fun `a change in one repository is found by a verified update`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            workspace.write("application/app/src/main/kotlin/org/example/app/Main.kt", "fun main() { println() }\n")
            val report = engine.update(verify = true)
            assertTrue(report.verify?.matches == true, "${report.verify?.differences}")
            assertTrue(report.unitsProduced > 0)
        }
    }

    @Test
    fun `a producer of repository units is run once per repository with the checkout of that repository`() =
        TwoRepositoryWorkspace().use { workspace ->
            val producer = RevisionProducer("history")
            workspace.engine(coreRegistry(producer)).use { engine ->
                engine.update()
                assertEquals(2, producer.units.size)
                val roots = producer.checkouts.map { checkNotNull(it).root.fileName.toString() }
                assertEquals(listOf("application", "library"), roots.sorted())
                assertTrue(producer.units.all { "settings.gradle.kts" in it })
            }
        }

    @Test
    fun `an index of several repositories is the same after a rebuild`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            val before = engine.graph().structure()
            engine.rebuild()
            assertEquals(before, engine.graph().structure())
        }
    }

    @Test
    fun `a root of checkouts without a commit indexes none of their files`() = TestWorkspace().use { workspace ->
        listOf("first", "second").forEach { name ->
            workspace.write("$name/a.txt", "x")
            TestGit.output(workspace.root.resolve(name), "init", "-q")
        }
        workspace.engine().use { engine ->
            val report = engine.update(verify = true)
            assertEquals(emptySet(), engine.artifactPaths())
            assertEquals(emptyList(), engine.graph().structure().repositories)
            assertTrue(report.verify?.matches == true)
        }
    }
}
