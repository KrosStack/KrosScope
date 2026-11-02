package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoordinateResolutionTest {
    private val draft = WorkspaceModelDraft()

    init {
        draft.repository("application")
        draft.repository("library")
    }

    private val app = draft.module("application", ":app")
    private val model = ModuleSpec(publications = "org.example:model:1.2.0")

    private fun Relationship.target() = (resolution as Resolution.Resolved).target

    private fun Relationship.certainty() = (resolution as Resolution.Resolved).certainty

    @Test
    fun `a dependency on a coordinate that another repository publishes becomes an edge to its module`() {
        val producer = draft.module("library", ":model", model)
        draft.dependsOn(app, "org.example:model", "1.2.0")

        val links = draft.resolve()
        val edge = links.relationships.single()

        assertEquals("maven-coordinates", edge.subKind)
        assertEquals(NodeRef.OfModule(producer), edge.target())
        assertEquals(Certainty.EXACT, edge.certainty())
        assertEquals("true", edge.attributes["versionMatch"])
        assertEquals("1.2.0", edge.attributes["version"])
        assertEquals("1.2.0", edge.attributes["sourceVersion"])
        assertEquals("implementation", edge.attributes["configuration"])
        assertEquals(emptyList(), links.diagnostics)
    }

    @Test
    fun `a coordinate is not matched inside the repository of the consumer`() {
        draft.module("application", ":sibling", ModuleSpec(publications = "org.example:sibling:1.0.0"))
        draft.dependsOn(app, "org.example:sibling", "1.0.0")

        assertEquals(emptyList(), draft.resolve().relationships)
    }

    @Test
    fun `a coordinate that no module publishes is left alone`() {
        draft.module("library", ":model", model)
        draft.dependsOn(app, "org.other:thing", "1.0.0")

        val links = draft.resolve()

        assertEquals(emptyList(), links.relationships)
        assertEquals(emptyList(), links.diagnostics)
    }

    @Test
    fun `two modules that publish the coordinate are reported and none is linked`() {
        draft.module("library", ":model", model)
        draft.repository("fork")
        draft.module("fork", ":model", model)
        draft.dependsOn(app, "org.example:model", "1.2.0")

        val links = draft.resolve()
        val diagnostic = links.diagnostics.single()

        assertEquals(emptyList(), links.relationships)
        assertEquals("workspace.coordinate-ambiguous", diagnostic.code.value)
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
        assertTrue("fork (:model), library (:model)" in diagnostic.message)
    }

    @Test
    fun `another version is a heuristic edge with a warning`() {
        draft.module("library", ":model", model)
        draft.dependsOn(app, "org.example:model", "0.9.0", line = 7)

        val links = draft.resolve()
        val edge = links.relationships.single()
        val diagnostic = links.diagnostics.single()

        assertEquals("false", edge.attributes["versionMatch"])
        assertEquals(Certainty.HEURISTIC, edge.certainty())
        assertEquals("workspace.coordinate-version-mismatch", diagnostic.code.value)
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
        assertEquals(
            "application (:app) pins org.example:model:0.9.0, but library (:model) builds 1.2.0 from source. " +
                "References across this dependency are heuristic.",
            diagnostic.message,
        )
        assertEquals(7, diagnostic.location?.range?.startLine)
    }

    @Test
    fun `a module that publishes several versions matches the one a consumer pins`() {
        val both = ModuleSpec(publications = "org.example:model:1.0.0,org.example:model:2.0.0")
        val producer = draft.module("library", ":model", both)
        draft.dependsOn(app, "org.example:model", "2.0.0")

        val links = draft.resolve()
        val edge = links.relationships.single()

        assertEquals(NodeRef.OfModule(producer), edge.target())
        assertEquals(Certainty.EXACT, edge.certainty())
        assertEquals("true", edge.attributes["versionMatch"])
        assertEquals("2.0.0", edge.attributes["sourceVersion"])
        assertEquals(emptyList(), links.diagnostics)
    }

    @Test
    fun `a pin that no publication of the module has is a mismatch against its first publication`() {
        draft.module("library", ":model", ModuleSpec(publications = "org.example:model:1.0.0,org.example:model:2.0.0"))
        draft.dependsOn(app, "org.example:model", "3.0.0")

        val links = draft.resolve()

        assertEquals("false", links.relationships.single().attributes["versionMatch"])
        assertEquals("1.0.0", links.relationships.single().attributes["sourceVersion"])
        assertEquals("workspace.coordinate-version-mismatch", links.diagnostics.single().code.value)
    }

    @Test
    fun `a missing, dynamic or ranged version is unknown`() {
        draft.module("library", ":model", model)
        listOf(null, "1.+", "latest.release", "[1.0,2.0)").forEachIndexed { line, version ->
            draft.dependsOn(app, "org.example:model", version, line + 1)
        }

        val links = draft.resolve()

        assertEquals(List(4) { "unknown" }, links.relationships.map { it.attributes["versionMatch"] })
        assertEquals(List(4) { "workspace.coordinate-version-unknown" }, links.diagnostics.map { it.code.value })
        assertTrue(links.diagnostics.all { it.severity == DiagnosticSeverity.INFO })
        assertNull(links.relationships.first().attributes["version"])
    }

    @Test
    fun `a blank version is reported as no fixed version and is not recorded`() {
        draft.module("library", ":model", model)
        draft.dependsOn(app, "org.example:model", "")

        val links = draft.resolve()

        assertEquals(
            "application (:app) pins org.example:model at no fixed version, which cannot be compared with 1.2.0 " +
                "that library (:model) builds from source. References across this dependency are heuristic.",
            links.diagnostics.single().message,
        )
        assertNull(links.relationships.single().attributes["version"])
        assertEquals("unknown", links.relationships.single().attributes["versionMatch"])
    }
}
