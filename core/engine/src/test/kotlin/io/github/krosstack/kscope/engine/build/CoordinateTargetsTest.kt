package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateTargetsTest {
    private val draft = WorkspaceModelDraft()

    init {
        draft.repository("application")
        draft.repository("library")
    }

    private val app = draft.module("application", ":app")

    private fun Relationship.target() = (resolution as Resolution.Resolved).target

    private fun Relationship.certainty() = (resolution as Resolution.Resolved).certainty

    @Test
    fun `a target artifact of a multiplatform module matches its root publication`() {
        val spec = ModuleSpec(
            publications = "org.example:shared:1.2.0",
            traits = setOf(ModuleTrait.KOTLIN_MULTIPLATFORM),
            targetNames = listOf("jvm", "android"),
        )
        val shared = draft.module("library", ":shared", spec)
        draft.dependsOn(app, "org.example:shared-jvm", "1.2.0")

        assertEquals(NodeRef.OfModule(shared), draft.resolve().relationships.single().target())
    }

    @Test
    fun `a target artifact is not derived for a module that publishes its targets`() {
        val spec = ModuleSpec(
            publications = "org.example:shared:1.2.0,org.example:shared-jvm:1.2.0",
            traits = setOf(ModuleTrait.KOTLIN_MULTIPLATFORM),
            targetNames = listOf("jvm", "android"),
        )
        draft.module("library", ":shared", spec)
        draft.dependsOn(app, "org.example:shared-android", "1.2.0", line = 3)
        draft.dependsOn(app, "org.example:shared-jvm-jvm", "1.2.0", line = 4)
        draft.dependsOn(app, "org.example:shared-jvm-android", "1.2.0", line = 5)
        draft.dependsOn(app, "org.example:shared-jvm", "1.2.0", line = 6)

        assertEquals(listOf(6), draft.resolve().relationships.map { it.site?.range?.startLine })
    }

    @Test
    fun `target artifacts are derived only from the root publications that have no target publication`() {
        val spec = ModuleSpec(
            publications = "org.example:first:1.2.0,org.example:first-jvm:1.2.0,org.example:second:1.2.0",
            traits = setOf(ModuleTrait.KOTLIN_MULTIPLATFORM),
            targetNames = listOf("jvm", "android"),
        )
        draft.module("library", ":shared", spec)
        draft.dependsOn(app, "org.example:first-android", "1.2.0", line = 3)
        draft.dependsOn(app, "org.example:second-android", "1.2.0", line = 4)

        assertEquals(listOf(4), draft.resolve().relationships.map { it.site?.range?.startLine })
    }

    @Test
    fun `a matching version stays heuristic when the publication is heuristic`() {
        draft.module("library", ":model", ModuleSpec(publications = "org.example:model:1.2.0", heuristic = true))
        draft.dependsOn(app, "org.example:model", "1.2.0")

        val edge = draft.resolve().relationships.single()

        assertEquals(Certainty.HEURISTIC, edge.certainty())
        assertEquals("true", edge.attributes["versionMatch"])
    }

    @Test
    fun `a publication with an unspecified version is linked heuristically by group and artifact`() {
        val spec = ModuleSpec(publications = "org.example.shared:core:unspecified")
        val producer = draft.module("library", ":model", spec)
        draft.dependsOn(app, "org.example.shared:core", "3.9.0")

        val links = draft.resolve()
        val edge = links.relationships.single()

        assertEquals(NodeRef.OfModule(producer), edge.target())
        assertEquals(Certainty.HEURISTIC, edge.certainty())
        assertEquals("unknown", edge.attributes["versionMatch"])
        assertEquals("3.9.0", edge.attributes["version"])
        assertEquals("unspecified", edge.attributes["sourceVersion"])
        val diagnostic = links.diagnostics.single()
        assertEquals("workspace.coordinate-version-unknown", diagnostic.code.value)
        assertEquals(DiagnosticSeverity.INFO, diagnostic.severity)
        assertEquals(
            "application (:app) pins org.example.shared:core at 3.9.0, but the version that library (:model) " +
                "builds from source is not read. References across this dependency are heuristic.",
            diagnostic.message,
        )
    }
}
