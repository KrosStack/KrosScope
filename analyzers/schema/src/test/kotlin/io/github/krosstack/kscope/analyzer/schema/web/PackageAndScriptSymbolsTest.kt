package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.analyzer.schema.web.output.viteConfig
import io.github.krosstack.kscope.analyzer.schema.web.output.withConfig
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.spi.AnalysisUnit
import kotlin.test.Test
import kotlin.test.assertEquals

private fun packages(files: Map<String, String>) = WebWorkspace(files).produce().symbolsOfKind("npm.package")

class PackageAndScriptSymbolsTest {
    @Test
    fun `a package without a bundler has a version and no output`() {
        val symbol = packages(mapOf("package.json" to """{"name": "lib", "version": "2.0.1"}""")).single()

        assertEquals("lib 2.0.1", symbol.signature)
        assertEquals(mapOf("npm.version" to "2.0.1"), symbol.attributes)
    }

    @Test
    fun `a package without a version has its name as signature and no version attribute`() {
        val symbol = packages(mapOf("package.json" to """{"name": "lib"}""")).single()

        assertEquals("lib", symbol.signature)
        assertEquals(emptyMap(), symbol.attributes)
    }

    @Test
    fun `a package whose manifest names a bundler has the default output, which is not declared`() {
        val manifest = """{"name": "app", "version": "1", "devDependencies": {"vite": "5"}}"""

        assertEquals(
            mapOf(
                "npm.version" to "1",
                "bundle.output" to "dist",
                "bundle.output-declared" to "false",
                "bundle.bundler" to "vite",
            ),
            packages(mapOf("package.json" to manifest)).single().attributes,
        )
    }

    @Test
    fun `a package whose config computes the output has no output attributes`() {
        val files = withConfig("vite.config.ts", viteConfig("build: { outDir: process.env.OUT }")) +
            mapOf("package.json" to """{"name": "app"}""")

        assertEquals(emptyMap(), packages(files).single().attributes)
    }

    @Test
    fun `the packages of a module are told apart by the file that declares them`() {
        val files = mapOf(
            "package.json" to """{"name": "root"}""",
            "web/package.json" to """{"name": "web"}""",
            "web/vite.config.ts" to viteConfig("""build: { outDir: "build" }"""),
            "other/package.json" to """{"name": "web"}""",
        )
        val symbols = packages(files)

        val declaredIn = symbols.map { it.nameLocation?.artifact?.path?.value }

        assertEquals(listOf("web", "root", "web"), symbols.map { it.name })
        assertEquals(listOf("other/package.json", "package.json", "web/package.json"), declaredIn)
        assertEquals(3, symbols.map { it.id }.toSet().size)
        assertEquals(listOf(null, null, "web/build"), symbols.map { it.attributes["bundle.output"] })
    }

    @Test
    fun `a manifest without a name leaves no fact and one that is no JSON object leaves a note`() {
        val nameless = WebWorkspace(mapOf("package.json" to """{"version": "1"}""")).produce()
        val invalid = WebWorkspace(mapOf("package.json" to "[1, 2]")).produce()
        val note = invalid.facts().filterIsInstance<Fact.OfDiagnostic>().single().value

        assertEquals(emptyList(), nameless.facts())
        assertEquals("web.invalid-manifest", note.code.value)
        assertEquals(DiagnosticSeverity.INFO, note.severity)
    }

    @Test
    fun `a hand-written script of each language becomes a file symbol and a declaration file does not`() {
        val files = mapOf(
            "src/a.ts" to "export const a = 1;\n",
            "src/b.tsx" to "export const b = <div/>;\n",
            "src/c.js" to "export const c = 1;\n",
            "src/d.mjs" to "export const d = 1;\n",
            "src/types.d.ts" to "export type T = string;\n",
            "src/empty.ts" to "",
        )
        val batches = WebWorkspace(files).produce()

        val typescript = batches.symbolsOfKind("typescript.file").map { it.qualifiedName }
        val javascript = batches.symbolsOfKind("javascript.file").map { it.qualifiedName }

        assertEquals(listOf("src/a.ts", "src/b.tsx", "src/empty.ts"), typescript)
        assertEquals(listOf("src/c.js", "src/d.mjs"), javascript)
    }

    @Test
    fun `a file that no module contains leaves no fact`() {
        val workspace = WebWorkspace(mapOf("src/a.ts" to "export {};\n"), moduleDirectory = "web")
        val unit = AnalysisUnit(
            OwnershipUnit(OwnershipKind.REPOSITORY, "stray"),
            workspace.artifacts.map { it.copy(sourceSet = null) },
            sourceSet = null,
            module = null,
        )
        val batches = mutableListOf<FactBatch>()
        WebBundleProducer().produce(workspace.request(listOf(unit))) { batches += it }

        assertEquals(emptyList(), batches.facts())
    }
}
