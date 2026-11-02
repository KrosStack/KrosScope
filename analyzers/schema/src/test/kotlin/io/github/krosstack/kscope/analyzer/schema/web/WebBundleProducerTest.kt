package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.relationships
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun Symbol.line() = requireNotNull(nameLocation).range.startLine

class WebBundleProducerTest {
    private val workspace = WebWorkspace(tasktideWeb, outputDirectories = listOf("dist"))

    private fun batches() = workspace.produce()

    @Test
    fun `the producer reads the five types of web files, one repository at a time`() {
        val descriptor = WebBundleProducer().descriptor

        assertEquals("kscope.web.bundles", descriptor.producer.id)
        assertEquals("0.1.0", descriptor.producer.version)
        assertEquals(
            setOf("bundler.config", "npm.manifest", "typescript.source", "javascript.source", "source-map"),
            descriptor.consumes.map { it.id }.toSet(),
        )
        assertEquals(OwnershipKind.REPOSITORY, descriptor.granularity)
    }

    @Test
    fun `the package json becomes an npm package symbol at its name, with the bundle output`() {
        val symbol = batches().symbolsOfKind("npm.package").single()

        assertEquals("@tasktide/web", symbol.name)
        assertEquals("@tasktide/web", symbol.qualifiedName)
        assertEquals("@tasktide/web 0.4.0", symbol.signature)
        assertEquals(2, symbol.line())
        assertEquals("BUILD", symbol.kind.category.name)
        assertEquals(
            mapOf(
                "npm.version" to "0.4.0",
                "bundle.output" to "dist",
                "bundle.output-declared" to "true",
                "bundle.bundler" to "vite",
            ),
            symbol.attributes,
        )
        assertEquals(":@main:npm/`package.json`:`@tasktide/web`.", Render.id(symbol.id))
        assertEquals("repo//:@main", symbol.scope.sourceSet?.value)
    }

    @Test
    fun `a hand-written script becomes a file symbol at its first line, the config and the bundle do not`() {
        val files = batches().symbolsOfKind("typescript.file")

        assertEquals(listOf("src/main.ts", "src/tasks.ts"), files.map { it.qualifiedName })
        assertEquals(listOf("main.ts", "tasks.ts"), files.map { it.name })
        assertEquals(listOf(1, 1), files.map { it.line() })
        assertEquals(
            listOf(":@main:typescript/`src/main.ts`:", ":@main:typescript/`src/tasks.ts`:"),
            files.map { Render.id(it.id) },
        )
        assertEquals(emptyList(), batches().symbolsOfKind("javascript.file"))
    }

    @Test
    fun `a bundle file that a source map maps becomes a bundle file symbol at its first line`() {
        val symbol = batches().symbolsOfKind("bundle.file").single()

        assertEquals("index.js", symbol.name)
        assertEquals("dist/assets/index.js", symbol.qualifiedName)
        assertEquals(1, symbol.line())
        assertEquals(EvidenceClass.CONTENT, symbol.evidenceClass)
        assertEquals(":@main:bundle/`dist/assets/index.js`:", Render.id(symbol.id))
        assertEquals("dist/assets/index.js", symbol.nameLocation?.artifact?.path?.value)
    }

    @Test
    fun `the bundle file depends on each source its map names, with the ids that the producer gives those files`() {
        val facts = batches().facts()
        val bundle = facts.symbols().single { it.kind.id == "bundle.file" }
        val scripts = facts.symbols().filter { it.kind.id == "typescript.file" }.associateBy { it.qualifiedName }
        val links = facts.relationships()

        val from = ":@main:bundle/`dist/assets/index.js`:"
        assertEquals(
            listOf(
                "DEPENDS_ON/source-map $from -> :@main:typescript/`src/tasks.ts`: CONTENT",
                "DEPENDS_ON/source-map $from -> :@main:typescript/`src/main.ts`: CONTENT",
            ),
            links.map { Render.relationship(it) },
        )
        assertEquals(
            listOf(scripts.getValue("src/tasks.ts").id, scripts.getValue("src/main.ts").id),
            links.map { ((it.resolution as Resolution.Resolved).target as NodeRef.OfSymbol).id },
        )
        links.forEach {
            assertEquals(NodeRef.OfSymbol(bundle.id), it.from)
            assertEquals(RelationKind.DEPENDS_ON, it.kind)
            assertEquals(Certainty.EXACT, (it.resolution as Resolution.Resolved).certainty)
            assertEquals(bundle.declaration, it.site)
            assertEquals(bundle.scope, it.scope)
        }
    }

    @Test
    fun `every repository answers with a batch of its own`() {
        val result = batches()

        assertEquals(listOf("repo"), result.map { it.owner.key })
        assertTrue(result.all { it.owner.kind == OwnershipKind.REPOSITORY && it.producer.id == "kscope.web.bundles" })
    }

    @Test
    fun `without a build there are no bundle files and no links`() {
        val unbuilt = WebWorkspace(tasktideWeb.filterKeys { !it.startsWith("dist/") }, listOf("dist"))
        val facts = unbuilt.produce().facts()

        assertEquals(emptyList(), facts.symbols().filter { it.kind.id == "bundle.file" })
        assertEquals(emptyList(), facts.relationships())
        assertEquals(3, facts.symbols().size)
    }

    @Test
    fun `no fact holds an absolute path`() {
        val text = batches().facts().joinToString("\n") { it.toString() }

        assertTrue(":\\" !in text && "file://" !in text && "/tmp" !in text && "/home" !in text, text)
    }

    @Test
    fun `producing twice gives the same facts`() {
        assertEquals(batches(), batches())
    }
}
