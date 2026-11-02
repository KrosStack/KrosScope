package io.github.krosstack.kscope.engine.build

import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateOrderTest {
    private val model = ModuleSpec(publications = "org.example:model:1.2.0")

    private fun resolved(repositories: List<String>, pins: List<Pair<String, Int>>): CoordinateLinks {
        val draft = WorkspaceModelDraft()
        repositories.forEach(draft::repository)
        draft.module("library", ":model", model)
        val app = draft.module("application", ":app")
        pins.forEach { (version, line) -> draft.dependsOn(app, "org.example:model", version, line) }
        return draft.resolve()
    }

    @Test
    fun `the output does not depend on the order of the inputs`() {
        val first = resolved(listOf("application", "library"), listOf("1.2.0" to 1, "0.9.0" to 2))
        val second = resolved(listOf("library", "application"), listOf("0.9.0" to 2, "1.2.0" to 1))

        assertEquals(first.relationships.map { it.attributes }, second.relationships.map { it.attributes })
        assertEquals(first.diagnostics.map { it.message }, second.diagnostics.map { it.message })
    }

    @Test
    fun `diagnostics of one message at different lines keep their order whatever the input order`() {
        val first = resolved(listOf("application", "library"), listOf("0.9.0" to 1, "0.9.0" to 2))
        val second = resolved(listOf("library", "application"), listOf("0.9.0" to 2, "0.9.0" to 1))

        assertEquals(listOf(1, 2), first.diagnostics.map { it.location?.range?.startLine })
        assertEquals(first.diagnostics, second.diagnostics)
    }

    @Test
    fun `edges that differ only in the pinned version keep their order whatever the input order`() {
        val first = resolved(listOf("application", "library"), listOf("0.8.0" to 3, "0.9.0" to 3))
        val second = resolved(listOf("library", "application"), listOf("0.9.0" to 3, "0.8.0" to 3))

        assertEquals(listOf("0.8.0", "0.9.0"), first.relationships.map { it.attributes["version"] })
        assertEquals(first.relationships, second.relationships)
    }
}
