package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateMatchWithoutLocationTest {
    private val draft = GraphDraft()
    private val app = draft.sourceSet(":app", "main", SetShape(repository = "application"))
    private val lib = draft.sourceSet(":lib", "main", SetShape(repository = "library"))
    private val linker = QualifiedNameLinker()

    private fun edges() = linker.linkedFrom(draft)

    @Test
    fun `a reference without a site finds the symbols of its own repository without any dependency`() {
        val user = draft.typeWithoutLocation(app, "p.app.User")
        val sibling = draft.sourceSet(":sibling", "main", SetShape(repository = "application"))
        val own = draft.type(sibling, "p.app.Greeter")
        draft.unresolved(user, RelationKind.IMPORTS, "p.app.Greeter")

        val edge = edges().single()

        assertEquals("qualified-name-match", edge.subKind)
        assertEquals(Resolution.Resolved(own.node, Certainty.HEURISTIC), edge.resolution)
    }

    @Test
    fun `a symbol without a declaration in another repository is not an answer without a dependency`() {
        val user = draft.type(app, "p.app.User")
        draft.typeWithoutLocation(lib, "p.lib.Generated")
        draft.unresolved(user, RelationKind.IMPORTS, "p.lib.Generated")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `a symbol without a declaration in another repository is answered through a dependency`() {
        val user = draft.type(app, "p.app.User")
        val generated = draft.typeWithoutLocation(lib, "p.lib.Generated")
        draft.dependency(":app", ":lib")
        draft.unresolved(user, RelationKind.IMPORTS, "p.lib.Generated")

        val edge = edges().single()

        assertEquals("coordinate-match", edge.subKind)
        assertEquals(Resolution.Resolved(generated.node, Certainty.HEURISTIC), edge.resolution)
    }
}
