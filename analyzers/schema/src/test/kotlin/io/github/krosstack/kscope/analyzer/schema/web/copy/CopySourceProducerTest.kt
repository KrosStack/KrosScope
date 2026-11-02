package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.generator.RepositorySpec
import io.github.krosstack.kscope.analyzer.schema.generator.lineOf
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.OwnershipKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CopySourceProducerTest {
    @Test
    fun `the producer reads the build scripts of the workspace and answers with one batch`() =
        tasktideWorld().use { world ->
            val descriptor = CopySourceProducer().descriptor
            val batch = world.copyBatches.single()

            assertEquals("kscope.web.copy-sources", descriptor.producer.id)
            assertEquals("0.1.0", descriptor.producer.version)
            assertEquals(setOf("gradle.build-script"), descriptor.consumes.map { it.id }.toSet())
            assertEquals(OwnershipKind.WORKSPACE, descriptor.granularity)
            assertEquals(OwnershipKind.WORKSPACE, batch.owner.kind)
            assertEquals("workspace", batch.owner.key)
        }

    @Test
    fun `a workspace whose scripts copy nothing gets an empty batch`() =
        tasktideWorld(server = "plugins { }\n").use { world ->
            assertEquals(emptyList(), world.copyBatches.single().facts)
        }

    @Test
    fun `a copied path is a symbol of the module of the script, at its from call, with no source set`() =
        tasktideWorld().use { world ->
            val symbol = world.sources().single()
            val line = lineOf(SERVER_SCRIPT, "from(")

            assertEquals("gradle.copy-source", symbol.kind.id)
            assertEquals("BUILD", symbol.kind.category.name)
            assertEquals("../tasktide-web/dist", symbol.name)
            assertEquals("\$rootDir/../tasktide-web/dist", symbol.signature)
            assertEquals("$SERVER#processResources.from", symbol.qualifiedName)
            assertEquals(SERVER, symbol.nameLocation?.artifact?.path?.value)
            assertEquals(line, symbol.nameLocation?.range?.startLine)
            assertEquals(line, symbol.declaration?.range?.startLine)
            assertEquals(EvidenceClass.BUILD_MODEL, symbol.evidenceClass)
            assertNull(symbol.scope.sourceSet)
            assertEquals(
                ":server@.:`$SERVER`:`processResources.from`:`\$rootDir/../tasktide-web/dist`.",
                Render.id(symbol.id),
            )
        }

    @Test
    fun `the symbol records the task, the destination, and where the path lies in the workspace`() =
        tasktideWorld().use { world ->
            assertEquals(
                mapOf(
                    "copy.task" to "processResources",
                    "copy.into" to "static",
                    "copy.repository" to "tasktide-web",
                    "copy.path" to "dist",
                ),
                world.sources().single().attributes,
            )
        }

    @Test
    fun `a name is the path after its anchor, or the literal when it has none or is nothing but the anchor`() =
        tasktideWorld(
            server = copying("\$rootDir", "\$projectDir/x/y", "x/z", "\${rootDir}/../a"),
        ).use { world ->
            assertEquals(listOf("\$rootDir", "x/y", "x/z", "../a"), world.sources().map { it.name })
        }

    @Test
    fun `a path that is copied twice by one task is two symbols with their own ids`() =
        tasktideWorld(server = copying("x", "x", "y")).use { world ->
            val symbols = world.sources()

            assertEquals(3, symbols.map { it.id }.toSet().size)
            assertEquals(listOf("x", "x", "y"), symbols.map { it.name })
        }

    @Test
    fun `the same path in two tasks of one script is two symbols`() = tasktideWorld(
        server = "processResources { from(\"x\") }\ntasks.register<Copy>(\"web\") { from(\"x\") }\n",
    ).use { world ->
        val symbols = world.sources()

        assertEquals(2, symbols.map { it.id }.toSet().size)
        assertEquals(listOf("$SERVER#processResources.from", "$SERVER#web.from"), symbols.map { it.qualifiedName })
        assertEquals(listOf("processResources", "web"), symbols.map { it.attributes["copy.task"] })
    }

    @Test
    fun `every script of every repository is read and the symbols come in the order of repository and path`() =
        tasktideWorld(
            appFiles = mapOf("build.gradle.kts" to "processResources { from(\"root\") }\n"),
            extra = listOf(
                RepositorySpec("aaa", mapOf("build.gradle.kts" to "processResources { from(\"first\") }\n")),
            ),
        ).use { world ->
            val symbols = world.sources()

            assertEquals(
                listOf("aaa/build.gradle.kts", "tasktide-app/build.gradle.kts", "tasktide-app/$SERVER"),
                symbols.map { it.file() },
            )
            assertEquals(listOf("first", "root", "../tasktide-web/dist"), symbols.map { it.name })
        }
}
