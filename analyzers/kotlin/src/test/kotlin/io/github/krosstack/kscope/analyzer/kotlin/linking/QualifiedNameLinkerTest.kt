package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

class QualifiedNameLinkerTest {
    private val draft = GraphDraft()
    private val app = draft.sourceSet(":app", "main")
    private val lib = draft.sourceSet(":lib", "main")
    private val linker = QualifiedNameLinker()

    private fun edges() = linker.linkedFrom(draft)

    @Test
    fun `an unresolved import of a unique workspace name gets an additional heuristic edge`() {
        val user = draft.type(app, "p.app.User")
        val target = draft.type(lib, "p.lib.Greeter")
        draft.unresolved(user, RelationKind.IMPORTS, "p.lib.Greeter")

        val edge = edges().single()

        assertEquals(RelationKind.IMPORTS, edge.kind)
        assertEquals("qualified-name-match", edge.subKind)
        assertEquals(Resolution.Resolved(target.node, Certainty.HEURISTIC), edge.resolution)
        assertEquals(EvidenceClass.TEXTUAL, edge.evidenceClass)
        assertEquals(user.symbol.declaration, edge.site)
        assertEquals(user.symbol.scope, edge.scope)
    }

    @Test
    fun `a name that two symbols have is not answered`() {
        val user = draft.type(app, "p.app.User")
        draft.type(lib, "p.lib.Greeter")
        draft.type(app, "p.lib.Greeter")
        draft.unresolved(user, RelationKind.IMPORTS, "p.lib.Greeter")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `a name that no workspace symbol has is not answered`() {
        val user = draft.type(app, "p.app.User")
        draft.unresolved(user, RelationKind.IMPORTS, "androidx.compose.runtime.Composable")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `a hint that is not a qualified name is not answered`() {
        val user = draft.type(app, "p.app.User")
        draft.type(lib, "Greeter")
        draft.unresolved(user, RelationKind.TYPE_USES, "Greeter")
        draft.unresolved(user, RelationKind.IMPORTS, "string/greeting")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `only the reasons of the Kotlin analyzer are answered`() {
        val user = draft.type(app, "p.app.User")
        draft.type(lib, "p.lib.Greeter")
        draft.unresolved(user, RelationKind.REFERENCES, "p.lib.Greeter", "jvm.class.not-found")
        draft.unresolved(user, RelationKind.CALLS, "p.lib.Greeter", "kotlin.ambiguous-reference")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `the same name in several imports gives an edge for each of them`() {
        val first = draft.type(app, "p.app.First")
        val second = draft.type(app, "p.app.Second")
        draft.type(lib, "p.lib.Greeter")
        draft.unresolved(first, RelationKind.IMPORTS, "p.lib.Greeter", "kotlin.unresolved-reference")
        draft.unresolved(second, RelationKind.IMPORTS, "p.lib.Greeter")

        assertEquals(2, edges().size)
    }
}
