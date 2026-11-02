package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.relationships
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

private const val MAP_PATH = "dist/assets/index.js.map"

/** A source map of version 3 with the given sources; the file and the source root are left out when null. */
internal fun mapJson(sources: List<String>, file: String? = "index.js", sourceRoot: String? = null): String {
    val fields = listOfNotNull(
        "\"version\":3",
        file?.let { "\"file\":\"$it\"" },
        sourceRoot?.let { "\"sourceRoot\":\"$it\"" },
        "\"sources\":[${sources.joinToString(",") { "\"$it\"" }}]",
        "\"mappings\":\"\"",
    )
    return fields.joinToString(",", "{", "}")
}

private fun built(vararg extra: Pair<String, String>) = WebWorkspace(tasktideWeb + extra, listOf("dist"))

private fun WebWorkspace.links(): List<Relationship> = produce().facts().relationships()

private fun WebWorkspace.bundleFiles(): List<String> =
    produce().symbolsOfKind("bundle.file").mapNotNull { it.qualifiedName }

private fun Relationship.targetNode(): NodeRef = (resolution as Resolution.Resolved).target

private fun WebWorkspace.targets(): List<String> = links().map { Render.node(it.targetNode()) }

private fun script(path: String) = ":@main:typescript/`$path`:"

class SourceMapLinksTest {
    @Test
    fun `a source that is not a hand-written script of the repository leaves no link`() {
        val sources = listOf(
            "../../src/main.ts",
            "../../src/missing.ts",
            "../../src/logo.svg",
            "../../../../outside.ts",
            "../../src/types.d.ts",
        )
        val workspace = built(
            MAP_PATH to mapJson(sources),
            "src/logo.svg" to "<svg/>",
            "src/types.d.ts" to "export {};",
        )

        assertEquals(listOf(script("src/main.ts")), workspace.targets())
    }

    @Test
    fun `a source that is a file of the output is no target`() {
        val workspace = built(
            MAP_PATH to mapJson(listOf("../chunk.js", "../../src/main.ts")),
            "dist/chunk.js" to "export {};\n",
        )

        assertEquals(listOf(script("src/main.ts")), workspace.targets())
    }

    @Test
    fun `the source root is put before the sources`() {
        val workspace = built(MAP_PATH to mapJson(listOf("main.ts", "tasks.ts"), sourceRoot = "../../src"))

        assertEquals(listOf(script("src/main.ts"), script("src/tasks.ts")), workspace.targets())
    }

    @Test
    fun `a webpack source is resolved against the package that built the bundle`() {
        val sources = listOf(
            "webpack://@tasktide/web/./src/main.ts",
            "webpack:///./src/tasks.ts",
            "webpack:///webpack/bootstrap",
        )
        val workspace = built(MAP_PATH to mapJson(sources))

        assertEquals(listOf(script("src/main.ts"), script("src/tasks.ts")), workspace.targets())
    }

    @Test
    fun `a webpack source of a package below the root is resolved against the package that holds the map`() {
        val files = mapOf(
            "web/package.json" to TASKTIDE_MANIFEST,
            "web/src/main.ts" to "export {};\n",
            "web/dist/index.js" to "a();\n",
            "web/dist/index.js.map" to mapJson(listOf("webpack://x/./src/main.ts"), file = null),
        )

        assertEquals(listOf(script("web/src/main.ts")), WebWorkspace(files, listOf("web/dist")).targets())
    }

    @Test
    fun `the file of the map is its file field, else the map without its suffix`() {
        val named = built(
            "dist/assets/chunk.js" to "b();\n",
            MAP_PATH to mapJson(listOf("../../src/main.ts"), file = "chunk.js"),
        )
        val unnamed = built(MAP_PATH to mapJson(listOf("../../src/main.ts"), file = null))

        assertEquals(listOf("dist/assets/chunk.js"), named.bundleFiles())
        assertEquals(listOf("dist/assets/index.js"), unnamed.bundleFiles())
    }

    @Test
    fun `a map whose file is not an artifact leaves no fact`() {
        val workspace = built(MAP_PATH to mapJson(listOf("../../src/main.ts"), file = "gone.js"))
        val facts = workspace.produce().facts()

        assertEquals(emptyList(), facts.symbols().filter { it.kind.id == "bundle.file" })
        assertEquals(emptyList(), facts.relationships())
    }

    @Test
    fun `a map outside the output roots is no output and is not read`() {
        val hand = mapOf("src/hand.js.map" to mapJson(listOf("main.ts"), file = "hand.js"), "src/hand.js" to "a();\n")
        val workspace = WebWorkspace(tasktideWeb + hand, listOf("dist"))
        val scripts = workspace.produce().symbolsOfKind("javascript.file")

        assertEquals(listOf("dist/assets/index.js"), workspace.bundleFiles())
        assertEquals(listOf("src/hand.js"), scripts.map { it.qualifiedName })
    }

    @Test
    fun `the bundle links to scripts of another source set with the ids that source set gives them`() {
        val workspace = WebWorkspace(tasktideWeb, listOf("dist"), mapOf("sources" to "src", "bundle" to "dist"))
        val facts = workspace.produce().single().facts
        val scripts = facts.symbols().filter { it.kind.id == "typescript.file" }
        val links = facts.relationships()

        assertEquals(listOf("src/main.ts", "src/tasks.ts"), scripts.map { it.qualifiedName })
        assertEquals(
            listOf(":@sources:typescript/`src/tasks.ts`:", ":@sources:typescript/`src/main.ts`:"),
            links.map { Render.node(it.targetNode()) },
        )
        assertEquals(
            scripts.map { NodeRef.OfSymbol(it.id) }.toSet(),
            links.map { it.targetNode() }.toSet(),
        )
    }
}
