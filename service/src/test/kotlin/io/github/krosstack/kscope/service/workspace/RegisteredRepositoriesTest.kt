package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.registry.RegistrationOutcome
import io.github.krosstack.kscope.service.registry.RepositoryRegistration
import io.github.krosstack.kscope.service.registry.RepositoryRegistry
import io.github.krosstack.kscope.service.trust.MachineDirectory
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegisteredRepositoriesTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `a kit registered from beside the app joins its workspace`() = WorkspaceOfRepositories().use { fixture ->
        val app = fixture.root.resolve("tasktide-app")
        val environment = mapOf(MachineDirectory.VARIABLE to fixture.root.resolve("home").toString())::get
        val registry = RepositoryRegistry.ofThisMachine(property = { null }, environment = environment)
        val outcome = RepositoryRegistration(app, registry).add(fixture.root.resolve("tasktide-kit").toString())
        assertTrue(outcome is RegistrationOutcome.Registered, "$outcome")

        KrosScope.open(app, OpenOptions(), environment).use { service ->
            service.scan()
            val graph = service.graph()
            assertEquals(listOf("", "tasktide-kit"), graph.structure().repositories.map { it.directory.value }.sorted())
            assertTrue(coordinateLinks(graph).contains(graph.module("", ":client") to graph.module(KIT, ":format")))
            val dueDates = graph.artifacts().map { it.ref }.single { it.path.value == DUE_DATES }
            val text = service.readWorkspaceFiles().read(dueDates).toString(Charsets.UTF_8)
            assertTrue("fun label" in text, text)
        }
    }

    @Test
    fun `a local checkout that takes the name of a registered repository keeps its own files`() {
        val base = Files.createTempDirectory("kscope-registered")
        try {
            val app = committed(base.resolve("workspace").resolve("app"), "App.kt", "class App")
            val external = committed(base.resolve("external-kit"), "Lib.kt", "class ExternalLib")
            val environment = mapOf(MachineDirectory.VARIABLE to base.resolve("home").toString())::get
            val registry = RepositoryRegistry.ofThisMachine(property = { null }, environment = environment)
            val workspace = app.parent
            val outcome = RepositoryRegistration(workspace, registry).add(external.toString(), name = "kit")
            assertTrue(outcome is RegistrationOutcome.Registered, "$outcome")
            KrosScope.open(workspace, OpenOptions(), environment).use { service ->
                service.scan()
                committed(workspace.resolve("kit"), "Lib.kt", "class LocalLib")
                service.scan()

                val library = service.graph().artifacts().map { it.ref }.single { it.path.value == "Lib.kt" }
                assertEquals("class LocalLib", service.readWorkspaceFiles().read(library).toString(Charsets.UTF_8))
            }
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a removed registration still reads its files until the next scan drops them`() {
        val base = Files.createTempDirectory("kscope-registered")
        try {
            val workspace = committed(base.resolve("workspace"), "App.kt", "class App")
            val external = committed(base.resolve("external-kit"), "Lib.kt", "class ExternalLib")
            val environment = mapOf(MachineDirectory.VARIABLE to base.resolve("home").toString())::get
            val registration = RepositoryRegistration(
                workspace,
                RepositoryRegistry.ofThisMachine(property = { null }, environment = environment),
            )
            assertTrue(registration.add(external.toString(), name = "kit") is RegistrationOutcome.Registered)
            KrosScope.open(workspace, OpenOptions(), environment).use { service ->
                service.scan()
                assertTrue(registration.remove("kit") is RegistrationOutcome.Removed)

                val library = service.graph().artifacts().map { it.ref }.single { it.path.value == "Lib.kt" }
                assertEquals("class ExternalLib", service.readWorkspaceFiles().read(library).toString(Charsets.UTF_8))

                service.scan()
                assertEquals(listOf("App.kt"), service.graph().artifacts().map { it.ref.path.value })
            }
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    private fun committed(directory: Path, file: String, text: String): Path {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve(file), text)
        TestGit.run(directory, listOf("init", "-q"))
        TestGit.run(directory, listOf("add", "-A"))
        TestGit.run(directory, listOf("commit", "-q", "-m", "initial"))
        return directory
    }

    private fun coordinateLinks(graph: GraphReader): List<Pair<NodeRef, NodeRef?>> =
        graph.relationships(RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), subKind = "maven-coordinates"))
            .map { it.from to (it.resolution as? Resolution.Resolved)?.target }

    private fun GraphReader.module(directory: String, path: String): NodeRef = structure().let { structure ->
        val repository = structure.repositories.single { it.directory.value == directory }.id
        val roots = structure.buildRoots.filter { it.repository == repository }.map { it.id }.toSet()
        NodeRef.OfModule(structure.modules.single { it.path == path && it.buildRoot in roots }.id)
    }

    private companion object {
        const val KIT = "tasktide-kit"
        const val DUE_DATES = "format/src/main/kotlin/sample/tasktide/format/DueDates.kt"
    }
}
