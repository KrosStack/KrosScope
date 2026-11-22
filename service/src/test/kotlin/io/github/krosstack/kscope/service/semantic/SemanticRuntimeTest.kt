package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace.OTHER
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticRuntimeTest {
    @TempDir
    lateinit var root: Path

    private val scratch get() = root.resolveSibling("${root.fileName}-scratch")

    @Test
    fun `public advance stays active while publishing zero behind chunks and stops after publication`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { it.scan() }
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { scope ->
            scope.scan()
            repeat(scope.graph().artifacts().size) { assertTrue(scope.semantic.advance()) }
            assertEquals<Any>(true, scope.advanceSemanticIndex())
            assertEquals(SemanticState.USED, scope.semanticStatus().state)
            assertEquals(0, scope.semanticStatus().behindChunks)
            assertNull(scope.semanticNotice())
            assertEquals<Any>(false, scope.advanceSemanticIndex())
            assertEquals<Any>(false, scope.advanceSemanticIndex())
        }
    }

    @Test
    fun `unknown inventory size is not reported fresh and the count is exact once known`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { scope ->
            scope.scan()
            assertEquals(0, fake.opened)
            val seen = mutableListOf<Pair<SemanticStatus, String?>>()
            assertTrue(scope.semantic.advance())
            seen += scope.semanticStatus() to scope.semanticNotice()
            while (scope.semantic.advance()) seen += scope.semanticStatus() to scope.semanticNotice()
            assertEquals(SemanticState.WORKER_UNAVAILABLE, seen.first().first.state)
            assertEquals("Semantic index is preparing.", seen.first().second)
            val behind = seen.map { it.first }.filter { it.state == SemanticState.BEHIND }
            assertTrue(behind.isNotEmpty() && behind.all { it.behindChunks == fake.provider.embedded.size })
            assertEquals(SemanticState.USED, scope.semanticStatus().state)
            assertEquals(0, fake.closed)
        }
    }

    @Test
    fun `late results of a superseded job are discarded`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { scope ->
            scope.scan()
            scope.semantic.advance()
            scope.semantic.advance()
            SemanticTestWorkspace.write(root, OTHER, "class Other {\n    fun third() = 4\n}\n")
            scope.scan()
            scope.semantic.advance()
            assertEquals(1, fake.closed)
            while (scope.semantic.advance()) continue
            assertEquals(2, fake.opened)
            assertEquals(SemanticState.USED, scope.semanticStatus().state)
            val generations = Files.list(root.resolve(".kscope")).use { files ->
                files.filter { it.fileName.toString().endsWith(".bin") }.count()
            }
            assertEquals(1L, generations)
        }
    }

    @Test
    fun `default options with an installed component embed nothing and open no worker`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        KrosScope.open(root, OpenOptions(), { null }, semanticComponents = fake).use { scope ->
            scope.scan()
            scope.scan(rebuild = true)
            scope.advanceSemanticIndex()
            assertEquals(0, fake.opened)
            assertTrue(fake.provider.embedded.isEmpty() && fake.provider.capCalls == 0)
            val derived = Files.list(root.resolve(".kscope")).use { files ->
                files.map { it.fileName.toString() }.filter { it.startsWith("embeddings") || it.startsWith("semantic") }
                    .sorted().toList()
            }
            assertEquals(listOf("embeddings.lock", "semantic-scan.lock"), derived)
        }
    }

    @Test
    fun `off and missing components start no worker`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.OFF).use { scope ->
            scope.scan()
            scope.advanceSemanticIndex()
            assertEquals(0, fake.opened)
            assertTrue(!Files.exists(root.resolve(".kscope/embeddings.json")))
        }
        val missing = FakeSemanticComponents(
            scratch,
            problem = SemanticComponents.Opening.Unavailable(SemanticState.NOT_INSTALLED, "Not installed"),
        )
        SemanticTestWorkspace.open(root, missing, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            assertEquals(SemanticState.NOT_INSTALLED, scope.semanticStatus().state)
            assertNull(scope.semanticNotice())
            assertTrue(missing.provider.embedded.isEmpty())
            assertTrue(!Files.exists(root.resolve(".kscope/embeddings.json")))
        }
    }
}
