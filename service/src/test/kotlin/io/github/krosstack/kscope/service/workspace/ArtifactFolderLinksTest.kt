package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.registry.FolderOutcome
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArtifactFolderLinksTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    private fun GraphReader.libraryLinks(): List<Relationship> =
        relationships(RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), subKind = "jvm-library"))

    private fun GraphReader.moduleNames(): Map<NodeRef, String> = structure().modules.associate {
        NodeRef.OfModule(it.id) to it.path
    }

    private fun KrosScope.verifiedScan() {
        val verification = assertNotNull(scan(verify = true).verification)
        assertTrue(verification.matches, "${verification.differences}")
    }

    @Test
    fun `without a registered folder the dependency on a published coordinate stays external`() =
        ArtifactFolderMachine().use { machine ->
            machine.open().use { service ->
                machine.publish("org/example/core/Slugs.class")

                service.verifiedScan()

                assertEquals(emptyList(), service.graph().libraryLinks())
            }
        }

    @Test
    fun `a folder added after a scan links the consumer by content, and removing it takes the link away again`() =
        ArtifactFolderMachine().use { machine ->
            machine.open().use { service ->
                service.verifiedScan()
                machine.publish("org/example/core/Slugs.class")
                assertEquals(FolderOutcome.Registered(machine.mavenLocal), machine.folders.add(machine.mavenLocal))

                service.verifiedScan()
                val graph = service.graph()
                val link = graph.libraryLinks().single()
                val names = graph.moduleNames()

                assertEquals(":client", names[link.from])
                assertEquals(":core", names[(link.resolution as Resolution.Resolved).target])
                assertEquals(EvidenceClass.CONTENT, link.evidenceClass)
                assertEquals("matched", link.attributes["content"])
                assertEquals("core-1.0.jar", link.attributes["file"])

                assertEquals(FolderOutcome.Removed(machine.mavenLocal), machine.folders.remove(machine.mavenLocal))
                service.verifiedScan()
                assertEquals(emptyList(), service.graph().libraryLinks())
            }
        }

    @Test
    fun `a registered folder that gets the archive after a scan links it at the next scan`() =
        ArtifactFolderMachine().use { machine ->
            machine.open().use { service ->
                Files.createDirectories(machine.mavenLocal)
                machine.folders.add(machine.mavenLocal)
                service.verifiedScan()
                val missing = service.graph().libraryLinks().single().resolution as Resolution.Unresolved
                assertEquals("artifact.output-missing", missing.reason.value)

                machine.publish("org/example/core/Slugs.class")
                service.verifiedScan()

                assertEquals("matched", service.graph().libraryLinks().single().attributes["content"])
            }
        }

    @Test
    fun `a published archive that changes its content changes the link at the next scan`() =
        ArtifactFolderMachine().use { machine ->
            machine.open().use { service ->
                machine.publish("org/example/core/Slugs.class")
                machine.folders.add(machine.mavenLocal)
                service.verifiedScan()
                assertEquals("matched", service.graph().libraryLinks().single().attributes["content"])

                machine.publish("org/example/core/Slugs.class", "org/example/other/Extra.class")
                service.verifiedScan()

                assertEquals("differs", service.graph().libraryLinks().single().attributes["content"])
            }
        }

    @Test
    fun `the aar of an android library in a registered folder links the consumer by content`() =
        ArtifactFolderMachine(android = true).use { machine ->
            machine.open().use { service ->
                machine.publish("org/example/core/Slugs.class")
                machine.folders.add(machine.mavenLocal)

                service.verifiedScan()
                val graph = service.graph()
                val link = graph.libraryLinks().single()

                assertEquals(":core", graph.moduleNames()[(link.resolution as Resolution.Resolved).target])
                assertEquals(EvidenceClass.CONTENT, link.evidenceClass)
                assertEquals("core-1.0.aar", link.attributes["file"])
            }
        }
}
