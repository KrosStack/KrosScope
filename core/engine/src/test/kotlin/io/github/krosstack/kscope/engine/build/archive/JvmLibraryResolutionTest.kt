package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.engine.build.ModuleSpec
import io.github.krosstack.kscope.engine.build.WorkspaceModelDraft
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmLibraryResolutionTest {
    private val workspace = JvmLibraryWorkspace()
    private val draft = workspace.draft

    @AfterTest
    fun clean() = workspace.close()

    private fun Relationship.resolved() = resolution as Resolution.Resolved

    @Test
    fun `a file dependency on a JAR that a module of another repository builds becomes an edge to that module`() {
        draft.dependsOnFile(workspace.client, "client/libs/text-1.0.0.jar", line = 9)

        val links = workspace.links()
        val edge = links.relationships.single()

        assertEquals("jvm-library", edge.subKind)
        assertEquals(NodeRef.OfModule(workspace.text), edge.resolved().target)
        assertEquals(NodeRef.OfModule(workspace.client), edge.from)
        assertEquals(EvidenceClass.BUILD_MODEL, edge.evidenceClass)
        assertEquals(Certainty.HEURISTIC, edge.resolved().certainty)
        assertEquals("text-1.0.0.jar", edge.attributes["file"])
        assertEquals("implementation", edge.attributes["configuration"])
        assertNull(edge.attributes["content"])
        assertEquals(9, edge.site?.range?.startLine)
        assertEquals(links.relationships, links.resolved())
    }

    @Test
    fun `a JAR is not matched inside the repository of the consumer`() {
        draft.module("application", ":sibling", ModuleSpec(archives = "sibling-1.0.0.jar"))
        draft.dependsOnFile(workspace.client, "client/libs/sibling-1.0.0.jar")

        assertEquals(emptyList(), workspace.links().relationships)
    }

    @Test
    fun `a JAR that no module builds stays an external file`() {
        draft.dependsOnFile(workspace.client, "client/libs/vendored-3.2.jar")

        assertEquals(emptyList(), workspace.links().relationships)
    }

    @Test
    fun `a dependency on a coordinate is not read as a file`() {
        draft.dependsOn(workspace.client, "sample:text-1.0.0.jar", "1.0.0")

        assertEquals(emptyList(), workspace.links().relationships)
    }

    @Test
    fun `two modules that build the JAR are reported as unresolved and none is linked`() {
        draft.repository("fork")
        draft.module("fork", ":text", ModuleSpec(archives = "text-1.0.0.jar"))
        draft.dependsOnFile(workspace.client, "client/libs/text-1.0.0.jar", line = 9)

        val links = workspace.links()
        val edge = links.relationships.single()
        val resolution = edge.resolution as Resolution.Unresolved

        assertEquals("jvm-library", edge.subKind)
        assertEquals("text-1.0.0.jar", resolution.hint)
        assertEquals("artifact.ambiguous-producer", resolution.reason.value)
        assertEquals(EvidenceClass.BUILD_MODEL, edge.evidenceClass)
        assertEquals(9, edge.site?.range?.startLine)
        assertEquals(emptyList(), links.resolved())
    }

    @Test
    fun `a directory names each JAR in it on disk, in order`() {
        draft.module("library", ":format", ModuleSpec(archives = "format-2.0.0.jar"))
        listOf("text-1.0.0.jar", "format-2.0.0.jar", "vendored.jar").forEach {
            JarFixtures.write(workspace.base.resolve("application/client/libs/$it"))
        }
        Files.writeString(workspace.base.resolve("application/client/libs/notes.txt"), "not a JAR")
        draft.dependsOnFile(workspace.client, "client/libs/")

        val edges = workspace.links().relationships

        assertEquals(listOf("format-2.0.0.jar", "text-1.0.0.jar"), edges.map { it.attributes["file"] })
        assertEquals(setOf("jvm-library"), edges.map { it.subKind }.toSet())
    }

    @Test
    fun `a directory that is missing names nothing`() {
        draft.dependsOnFile(workspace.client, "client/libs/")

        assertEquals(emptyList(), workspace.links().relationships)
    }

    @Test
    fun `the output does not depend on the order of the inputs`() {
        val first = declaredInOrder(listOf("text-1.0.0.jar", "format-2.0.0.jar"))
        val second = declaredInOrder(listOf("format-2.0.0.jar", "text-1.0.0.jar"))

        assertEquals(2, first.size)
        assertEquals(first, second)
    }

    private fun declaredInOrder(files: List<String>): List<Relationship> {
        val ordered = WorkspaceModelDraft()
        ordered.repository("application")
        ordered.repository("library")
        val client = ordered.module("application", ":client")
        ordered.module("library", ":text", ModuleSpec(archives = "text-1.0.0.jar"))
        ordered.module("library", ":format", ModuleSpec(archives = "format-2.0.0.jar"))
        files.forEach { ordered.dependsOnFile(client, "client/libs/$it") }
        return ordered.resolveJvmLibraries(workspace.base).relationships
    }
}
