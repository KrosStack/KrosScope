package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.TwoRepositoryWorkspace
import io.github.krosstack.kscope.engine.UpdateReport
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.structure.TestGit
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSetRole
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val NOTES_FILES = mapOf(
    "README.md" to "# Notes\n",
    "docs/guide.md" to "How to read the notes.\n",
    "native/lib.c" to "int answer(void) { return 42; }\n",
)

class RepositoryModuleTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    private fun UpdateReport.buildModelNotices() =
        diagnostics.map { it.code.value }.filter { it.startsWith("build-model.") }

    private fun withNotes(block: (TwoRepositoryWorkspace) -> Unit) = TwoRepositoryWorkspace().use { workspace ->
        workspace.createRepository("notes", NOTES_FILES)
        block(workspace)
    }

    @Test
    fun `a repository without a build gets one module that holds every file when the workspace has several`() =
        withNotes { workspace ->
            workspace.engine().use { engine ->
                engine.update()
                val structure = engine.graph().structure()
                val notes = structure.repositories.single { it.directory.value == "notes" }
                val root = structure.buildRoots.single { it.repository == notes.id }
                assertEquals("${notes.id.value}//", root.id.value)
                assertEquals(RepoPath(""), root.directory)
                assertEquals(BuildSystem.NONE, root.buildSystem)

                val module = structure.modules.single { it.buildRoot == root.id }
                assertEquals("${root.id.value}:", module.id.value)
                assertEquals(":", module.path)
                assertEquals(RepoPath(""), module.directory)
                assertEquals(emptyList(), module.plugins)
                assertEquals(emptySet(), module.traits)
                assertEquals(EvidenceClass.CONVENTION, module.evidenceClass)
                assertEquals(Certainty.HEURISTIC, module.certainty)
                assertEquals(mapOf("kscope.repository-module" to "true"), module.attributes)

                val sourceSet = structure.sourceSets.single { it.module == module.id }
                assertEquals("${module.id.value}@main", sourceSet.id.value)
                assertEquals(SourceSetRole.MAIN, sourceSet.role)
                assertEquals(listOf(SourceRoot(RepoPath(""), SourceRootKind("repository"), false)), sourceSet.roots)
            }
        }

    @Test
    fun `every file of that repository belongs to its source set and the other repositories are unchanged`() =
        withNotes { workspace ->
            workspace.engine().use { engine ->
                engine.update()
                val structure = engine.graph().structure()
                val notes = structure.repositories.single { it.directory.value == "notes" }
                val sourceSet = structure.sourceSets.single { it.module.value == notes.id.value + "//:" }
                val ofNotes = engine.graph().artifacts().filter { it.ref.repository == notes.id }
                assertEquals(NOTES_FILES.keys, ofNotes.map { it.ref.path.value }.toSet())
                assertTrue(ofNotes.all { it.sourceSet == sourceSet.id }, "$ofNotes")
                assertEquals(listOf(":", ":app", ":model"), structure.modules.map { it.path }.sorted())
                val marked = structure.modules.filter { "kscope.repository-module" in it.attributes }
                assertEquals(listOf(notes.id.value + "//:"), marked.map { it.id.value })
            }
        }

    @Test
    fun `the repository module replaces the notice that no model is available`() = withNotes { workspace ->
        workspace.engine().use { engine ->
            val report = engine.update()
            assertEquals(listOf("build-model.repository-module"), report.buildModelNotices())
            val notice = report.diagnostics.single { it.code.value == "build-model.repository-module" }
            assertEquals(DiagnosticSeverity.INFO, notice.severity)
            assertTrue("notes" in notice.message, notice.message)
        }
    }

    @Test
    fun `the module is stored once and an update that changes nothing produces nothing`() = withNotes { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            val second = engine.update()
            assertEquals(0, second.unitsProduced)
            assertEquals(listOf("build-model.repository-module"), second.buildModelNotices())
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `a repository that gains a build loses its repository module`() = withNotes { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            workspace.write("notes/settings.gradle.kts", "rootProject.name = \"notes\"\ninclude(\":core\")\n")
            workspace.write("notes/core/build.gradle.kts", "plugins { kotlin(\"jvm\") }\n")
            val report = engine.update()
            val structure = engine.graph().structure()
            val notes = structure.repositories.single { it.directory.value == "notes" }
            val roots = structure.buildRoots.filter { it.repository == notes.id }
            assertEquals(listOf(BuildSystem.GRADLE), roots.map { it.buildSystem })
            val rootIds = roots.map { it.id }
            assertEquals(listOf(":core"), structure.modules.filter { it.buildRoot in rootIds }.map { it.path })
            assertTrue(report.diagnostics.none { it.code.value == "build-model.repository-module" })
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `a workspace of one repository without a build keeps the notice and gets no module`() =
        TestWorkspace().use { workspace ->
            workspace.write("notes/README.md", "# Notes\n")
            workspace.engine().use { engine ->
                val report = engine.update()
                assertEquals(listOf("build-model.not-available"), report.buildModelNotices())
                assertEquals(emptyList(), engine.graph().structure().modules)
                assertNull(engine.graph().artifacts().first().sourceSet)
            }
        }
}
