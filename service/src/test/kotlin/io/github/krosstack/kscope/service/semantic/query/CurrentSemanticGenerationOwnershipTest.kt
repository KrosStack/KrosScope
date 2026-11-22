package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.semantic.chunk.ChunkKind
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CurrentSemanticGenerationOwnershipTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `identical type chunks at the same path credit both current repositories`() {
        assumeTrue(TestGit.available())
        SemanticQueryFixture(root, ::createRepositories).use { f ->
            val chunks = f.inventory.flatMap { it.toChunks() }
            val shared = chunks.filter { it.kind == ChunkKind.TYPE }.groupBy { it.key }.values.first {
                it.map { chunk -> chunk.artifact.repository }.distinct().size == 2
            }
            val artifacts = shared.map { it.artifact }.toSet()
            assertEquals(1, artifacts.map { it.path }.distinct().size)
            assertTrue(shared.flatMap { it.nodes }.all { it is NodeRef.OfSymbol })
            val snapshot = assertIs<CurrentSemanticGeneration.Opening.Ready>(f.current.open(f.provider)).snapshot
            val entry = snapshot.entries[chunks.map { it.key }.distinct().sorted().indexOf(shared.first().key)]
            assertEquals(artifacts, entry.artifacts)
            assertEquals(0, f.provider.capCalls)
            assertTrue(f.provider.embedded.isEmpty())
            f.current.close()
            f.assertNoLeases()
        }
    }

    @Test
    fun `a resource declared in two configurations keeps an unchanged inventory current`() {
        SemanticQueryFixture(root, ::createResources).use { f ->
            val resources = f.graph.artifacts().map { it.ref }.filter { it.path.value.endsWith("strings.xml") }
            val declared = resources.map { ref ->
                f.graph.symbols(SymbolQuery(artifact = ref, limit = Int.MAX_VALUE)).map { it.id }.toSet()
            }
            assertEquals(2, declared.size)
            assertTrue(declared[0].intersect(declared[1]).isNotEmpty())
            assertIs<CurrentSemanticGeneration.Opening.Ready>(f.current.open(f.provider))
            f.current.close()
            f.assertNoLeases()
        }
    }

    private fun createRepositories(directory: Path) {
        for (name in listOf("first", "second")) {
            val repository = directory.resolve(name)
            SemanticTestWorkspace.create(repository)
            TestGit.run(repository, listOf("init", "-q"))
            TestGit.run(repository, listOf("add", "-A"))
            TestGit.run(repository, listOf("commit", "-q", "-m", name))
        }
    }

    private fun createResources(directory: Path) {
        val files = mapOf(
            "settings.gradle.kts" to "include(\":app\")\n",
            "app/build.gradle.kts" to "plugins {\n    id(\"com.android.application\")\n}\n\n" +
                "android {\n    namespace = \"sample.app\"\n}\n",
            "app/src/main/AndroidManifest.xml" to "<manifest />\n",
            "app/src/main/kotlin/sample/app/Main.kt" to "package sample.app\n\nclass Main\n",
            "app/src/main/res/values/strings.xml" to strings("Title"),
            "app/src/main/res/values-de/strings.xml" to strings("Titel"),
        )
        files.forEach { (path, text) -> SemanticTestWorkspace.write(directory, path, text) }
    }

    private fun strings(title: String) = "<resources>\n    <string name=\"title\">$title</string>\n</resources>\n"
}
