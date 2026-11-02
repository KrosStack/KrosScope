package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.build.StructuralBuildModelProvider
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.engine.structure.TestGit
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal const val GENERATED_DIRECTORY = "app/build/generated/ksp/main/kotlin"

/** The structural model, plus the generated roots that the static Gradle provider would add for the app's main. */
internal class GeneratedRootProvider(
    private val delegate: StructuralBuildModelProvider = StructuralBuildModelProvider(),
) : BuildModelProvider {
    override val producer = ProducerRef("test.generated-roots", "1")
    override val requiresTrust = false

    override fun provide(request: BuildModelRequest): BuildModelResult {
        val provided = delegate.provide(request) as? BuildModelResult.Provided ?: return BuildModelResult.NotApplicable
        val sourceSets = provided.model.sourceSets.map { sourceSet ->
            if (!sourceSet.id.value.endsWith(":app@main")) return@map sourceSet
            val roots = listOf(GENERATED_DIRECTORY, "app/build/generated/not/there").map {
                SourceRoot(RepoPath(it), SourceRootKind("kotlin"), generated = true)
            }
            sourceSet.copy(roots = sourceSet.roots + roots)
        }
        return provided.copy(model = provided.model.copy(sourceSets = sourceSets))
    }
}

class GeneratedRootsTest {
    private val generatedFile = "$GENERATED_DIRECTORY/com/example/app/Generated.kt"
    private val notesFile = "$GENERATED_DIRECTORY/notes.txt"
    private val appMain = "app/src/main/kotlin/com/example/app/Main.kt"

    private fun registry(vararg extra: Any) = coreRegistry(*extra, providers = listOf(GeneratedRootProvider()))

    private fun TestWorkspace.withGeneratedOutput() {
        write(generatedFile, "package com.example.app\n\nclass Generated\n")
        write(notesFile, "output")
    }

    @Test
    fun `files below an existing generated root are inventoried with their generator and source set`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.withGeneratedOutput()
            workspace.engine(registry()).use { engine ->
                engine.update()
                val artifacts = engine.graph().artifacts().associateBy { it.ref.path.value }
                val generated = assertNotNull(artifacts[generatedFile])
                assertEquals(GeneratedInfo("ksp", null), generated.generated)
                assertEquals("kotlin.source", generated.type.id)
                assertTrue(generated.sourceSet?.value?.endsWith(":app@main") == true)
                assertEquals(GeneratedInfo("ksp", null), artifacts.getValue(notesFile).generated)
                assertNull(artifacts.getValue(appMain).generated)
                assertTrue(artifacts.keys.none { "not/there" in it })
            }
        }
    }

    @Test
    fun `exclude and include act on the files of generated roots too`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.withGeneratedOutput()
            workspace.engine(registry(), scope = { ScanScope(exclude = listOf("**/generated/**")) }).use { engine ->
                engine.update()
                assertTrue(engine.artifactPaths().none { "/generated/" in it })
            }
            workspace.engine(registry(), scope = { ScanScope(include = listOf("core/**")) }).use { engine ->
                engine.update()
                assertTrue(engine.artifactPaths().none { "/generated/" in it })
            }
        }
    }

    @Test
    fun `a generated root that does not exist adds nothing`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry()).use { engine ->
                engine.update()
                assertTrue(engine.graph().artifacts().none { it.generated != null })
            }
        }
    }

    @Test
    fun `generated files are inventoried but not handed to producers`() {
        val producer = RecordingProducer("by-artifact")
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.withGeneratedOutput()
            workspace.engine(registry(producer)).use { engine ->
                engine.update()
                assertTrue(generatedFile in engine.artifactPaths())
                assertTrue(generatedFile !in producer.contextPaths)
                assertTrue(producer.producedKeys.none { "Generated.kt" in it })
                assertEquals(6, producer.producedKeys.size)
            }
        }
    }

    @Test
    fun `a change in generated output changes the inventory and nothing else, and verify agrees`() {
        val producer = RecordingProducer("by-artifact")
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.withGeneratedOutput()
            workspace.engine(registry(producer)).use { engine ->
                engine.update()
                producer.calls.clear()
                workspace.write(generatedFile, "package com.example.app\n\nclass Generated2\n")
                val report = engine.update()
                assertEquals(1, report.unitsProduced, "Only the inventory is rebuilt")
                assertEquals(emptyList(), producer.producedKeys)
                engine.assertMatchesRebuild()
            }
        }
    }

    @Test
    fun `output that git ignores is found when the root exists on disk`() {
        assumeTrue(TestGit.available())
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.write(".gitignore", "build/\n")
            TestGit.output(workspace.root, "init", "-q")
            TestGit.output(workspace.root, "add", "-A")
            TestGit.output(workspace.root, "commit", "-q", "-m", "initial")
            workspace.withGeneratedOutput()
            workspace.engine(registry()).use { engine ->
                engine.update()
                assertTrue(generatedFile in engine.artifactPaths())
                assertNotNull(engine.graph().artifacts().single { it.ref.path.value == generatedFile }.generated)
            }
        }
    }

    @Test
    fun `a tracked file below a generated root is marked generated as well`() {
        assumeTrue(TestGit.available())
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.withGeneratedOutput()
            TestGit.output(workspace.root, "init", "-q")
            TestGit.output(workspace.root, "add", "-A", "-f")
            TestGit.output(workspace.root, "commit", "-q", "-m", "initial")
            workspace.engine(registry()).use { engine ->
                engine.update()
                val tracked = engine.graph().artifacts().single { it.ref.path.value == generatedFile }
                assertEquals(GeneratedInfo("ksp", null), tracked.generated)
            }
        }
    }
}
