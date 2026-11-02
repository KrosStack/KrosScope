package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.relationships
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val gradleModuleWeb = mapOf(
    "src/main/resources/legacy.js" to "export const legacy = 1;\n",
    "web/package.json" to """{"name": "@tasktide/web", "version": "1.0.0", "devDependencies": {"vite": "5"}}""",
    "web/src/app.ts" to "export const app = 1;\n",
    "web/dist/assets/index.js" to "a();\n//# sourceMappingURL=index.js.map\n",
    "web/dist/assets/index.js.map" to mapJson(listOf("../../src/app.ts")),
)

private fun gradleModule() = WebWorkspace(
    gradleModuleWeb,
    outputDirectories = listOf("web/dist"),
    sourceSetRoots = mapOf("main" to "src/main"),
)

class ModuleScriptsTest {
    @Test
    fun `a script beside the source sets of a module is a symbol of the module with no source set`() {
        val scripts = gradleModule().produce().symbolsOfKind("typescript.file")
        val script = scripts.single()

        assertEquals("web/src/app.ts", script.qualifiedName)
        assertEquals(":@.:typescript/`web/src/app.ts`:", Render.id(script.id))
        assertNull(script.scope.sourceSet)
    }

    @Test
    fun `a script in a source set keeps the identity that the source set gives it`() {
        val script = gradleModule().produce().symbolsOfKind("javascript.file").single()

        assertEquals("src/main/resources/legacy.js", script.qualifiedName)
        assertEquals(":@main:javascript/`src/main/resources/legacy.js`:", Render.id(script.id))
        assertEquals("repo//:@main", script.scope.sourceSet?.value)
    }

    @Test
    fun `the package beside the source sets is a symbol of the module as well`() {
        val symbol = gradleModule().produce().symbolsOfKind("npm.package").single()

        assertEquals("@tasktide/web", symbol.name)
        assertNull(symbol.scope.sourceSet)
        assertEquals("web/dist", symbol.attributes["bundle.output"])
    }

    @Test
    fun `a bundle beside the source sets is linked to the scripts beside them`() {
        val facts = gradleModule().produce().facts()
        val links = facts.relationships()

        val from = ":@.:bundle/`web/dist/assets/index.js`:"
        assertEquals(
            listOf("DEPENDS_ON/source-map $from -> :@.:typescript/`web/src/app.ts`: CONTENT"),
            links.map { Render.relationship(it) },
        )
    }

    @Test
    fun `the repository is one unit with a batch of its own`() {
        val workspace = gradleModule()

        assertEquals(listOf("repo"), workspace.produce().map { it.owner.key })
        assertEquals(1, workspace.units().size)
    }
}
