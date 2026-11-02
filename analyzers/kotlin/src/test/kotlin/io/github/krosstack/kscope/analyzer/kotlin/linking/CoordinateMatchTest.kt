package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateMatchTest {
    private val draft = GraphDraft()
    private val app = draft.sourceSet(":app", "main", SetShape(repository = "application"))
    private val lib = draft.sourceSet(":lib", "main", SetShape(repository = "library"))
    private val other = draft.sourceSet(":other", "main", SetShape(repository = "other"))
    private val linker = QualifiedNameLinker()

    private fun edges() = linker.linkedFrom(draft)

    @Test
    fun `a symbol of another repository without a dependency is not an answer`() {
        val user = draft.type(app, "p.app.User")
        draft.type(lib, "p.lib.Greeter")
        draft.unresolved(user, RelationKind.IMPORTS, "p.lib.Greeter")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `a symbol reached through a dependency of another repository is answered as a coordinate match`() {
        val user = draft.type(app, "p.app.User")
        val target = draft.type(lib, "p.lib.Greeter")
        draft.dependency(":app", ":lib")
        draft.unresolved(user, RelationKind.IMPORTS, "p.lib.Greeter")

        val edge = edges().single()

        assertEquals("coordinate-match", edge.subKind)
        assertEquals(Resolution.Resolved(target.node, Certainty.HEURISTIC), edge.resolution)
    }

    @Test
    fun `a symbol reached transitively is answered`() {
        val user = draft.type(app, "p.app.User")
        draft.type(other, "p.other.Greeter")
        draft.dependency(":app", ":lib")
        draft.dependency(":lib", ":other")
        draft.unresolved(user, RelationKind.IMPORTS, "p.other.Greeter")

        assertEquals("coordinate-match", edges().single().subKind)
    }

    @Test
    fun `two symbols in reach are not answered`() {
        val user = draft.type(app, "p.app.User")
        draft.type(lib, "p.shared.Greeter")
        draft.type(other, "p.shared.Greeter")
        draft.dependency(":app", ":lib")
        draft.dependency(":app", ":other")
        draft.unresolved(user, RelationKind.IMPORTS, "p.shared.Greeter")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `only the symbol in reach is an answer when another repository has the name too`() {
        val user = draft.type(app, "p.app.User")
        val reached = draft.type(lib, "p.shared.Greeter")
        draft.type(other, "p.shared.Greeter")
        draft.dependency(":app", ":lib")
        draft.unresolved(user, RelationKind.IMPORTS, "p.shared.Greeter")

        assertEquals(Resolution.Resolved(reached.node, Certainty.HEURISTIC), edges().single().resolution)
    }

    @Test
    fun `a symbol of the own repository wins over a reachable one`() {
        val user = draft.type(app, "p.app.User")
        val own = draft.type(app, "p.shared.Greeter")
        draft.type(lib, "p.shared.Greeter")
        draft.dependency(":app", ":lib")
        draft.unresolved(user, RelationKind.IMPORTS, "p.shared.Greeter")

        val edge = edges().single()

        assertEquals("qualified-name-match", edge.subKind)
        assertEquals(Resolution.Resolved(own.node, Certainty.HEURISTIC), edge.resolution)
    }

    @Test
    fun `two symbols of the own repository are not answered even when another one is reachable`() {
        val user = draft.type(app, "p.app.User")
        draft.type(app, "p.shared.Greeter")
        val sibling = draft.sourceSet(":sibling", "main", SetShape(repository = "application"))
        draft.type(sibling, "p.shared.Greeter")
        draft.type(lib, "p.shared.Greeter")
        draft.dependency(":app", ":lib")
        draft.unresolved(user, RelationKind.IMPORTS, "p.shared.Greeter")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `a reference the compiler resolved is not answered again, so an equal version stays exact`() {
        val user = draft.type(app, "p.app.User")
        val target = draft.type(lib, "p.lib.Greeter")
        draft.dependency(":app", ":lib")
        draft.relate(user, RelationKind.IMPORTS, target)

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `an unresolved reference across a dependency on another version is a heuristic textual answer`() {
        val user = draft.type(app, "p.app.User")
        val target = draft.type(lib, "p.lib.Greeter")
        draft.dependency(":app", ":lib")
        draft.unresolved(user, RelationKind.CALLS, "p.lib.Greeter")

        val edge = edges().single()

        assertEquals(Resolution.Resolved(target.node, Certainty.HEURISTIC), edge.resolution)
        assertEquals(EvidenceClass.TEXTUAL, edge.evidenceClass)
    }
}
