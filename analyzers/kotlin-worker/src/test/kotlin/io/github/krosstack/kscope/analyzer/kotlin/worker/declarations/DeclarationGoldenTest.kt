package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.analyzer.kotlin.worker.Golden
import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyze
import io.github.krosstack.kscope.model.Platform
import kotlin.test.Test

class DeclarationGoldenTest {
    private fun golden(name: String, vararg directoryAndModule: Pair<String, String>) {
        val modules = directoryAndModule.map { (module, directory) -> SampleWorkspace.singleModule(module, directory) }
        Golden.assertMatches("$name.jsonl", Golden.facts(analyze(modules)))
    }

    @Test
    fun `mixed Kotlin and Java files of one source set match the golden output`() =
        golden("mixed-kotlin-java", "mixed" to "mixed/main")

    @Test
    fun `nested companion object enum annotation and typealias declarations match the golden output`() =
        golden("declaration-shapes", "shapes" to "shapes/main")

    @Test
    fun `overloads and extension declarations match the golden output`() =
        golden("overloads-extensions", "overloads" to "overloads/main")

    @Test
    fun `constructors match the golden output`() = golden("constructors", "constructors" to "constructors/main")

    @Test
    fun `visibility and flags match the golden output`() = golden("visibility-flags", "flags" to "flags/main")

    @Test
    fun `test detection matches the golden output`() = golden("test-detection", "tests" to "tests/test")

    @Test
    fun `documentation summaries match the golden output`() = golden("documentation", "docs" to "docs/main")

    @Test
    fun `a file with syntax errors matches the golden output`() = golden("syntax-errors", "broken" to "broken/main")

    @Test
    fun `a module with two source sets matches the golden output`() {
        val module = SampleWorkspace.module(
            "app:main+debug",
            Platform.ANDROID,
            SampleWorkspace.sourceSet("app", "main", "merged/main"),
            SampleWorkspace.sourceSet("app", "debug", "merged/debug"),
        )
        Golden.assertMatches("merged-source-sets.jsonl", Golden.facts(analyze(module)))
    }

    @Test
    fun `a synthetic stub root adds no symbols to the batch`() {
        val module = SampleWorkspace.module(
            "gen",
            Platform.ANDROID,
            SampleWorkspace.sourceSet(
                "gen",
                "main",
                "generated/main",
                syntheticSources = listOf(SampleWorkspace.androidResourceStub()),
            ),
        )
        Golden.assertMatches("synthetic-stub-root.jsonl", Golden.facts(analyze(module)))
    }
}
