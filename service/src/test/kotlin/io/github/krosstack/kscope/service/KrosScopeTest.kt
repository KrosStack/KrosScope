package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.service.analysis.HISTORY_UNIT
import io.github.krosstack.kscope.service.analysis.linkerCount
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KrosScopeTest {
    // Discovery, the build model, the two source sets, the build config unit of the module, the load calls and the
    // Ktor routes of each of the two source sets, the generator inputs and the copy sources of the workspace and
    // the linkers.
    private val scanUnits = 11 + HISTORY_UNIT + linkerCount

    private fun workspace(): Path {
        val root = Files.createTempDirectory("kscope-service")
        write(root, "settings.gradle.kts", "include(\":lib\")\n")
        write(root, "lib/build.gradle.kts", "")
        write(root, "lib/src/main/kotlin/Lib.kt", "class Lib\n")
        write(root, "lib/src/test/kotlin/LibTest.kt", "class LibTest\n")
        write(root, "README.md", "# Sample\n")
        return root
    }

    private fun write(root: Path, relative: String, text: String) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun <T> inWorkspace(block: (Path) -> T): T {
        val root = workspace()
        try {
            return block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a workspace that was never scanned reports an empty index without creating one`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            val info = service.info()
            assertFalse(info.indexed)
            assertEquals(0, info.structure.modules)
            assertTrue(info.artifactsByType.isEmpty())
        }
        assertFalse(Files.exists(root.resolve(".kscope")))
    }

    @Test
    fun `a scan reports the units it produced and info summarizes the index`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            val scan = service.scan()
            assertEquals(5, scan.artifacts)
            assertEquals(scanUnits, scan.unitsProduced)
            assertEquals(0, scan.diagnostics.errors)
            assertNull(scan.verification)

            val info = service.info()
            assertTrue(info.indexed)
            assertEquals(StructureSummary(1, 1, 1, 2, 0, 0), info.structure)
            assertEquals(mapOf("kotlin.source" to 2L), info.artifactsByType.filterKeys { it == "kotlin.source" })
            assertEquals(5L, info.artifactsByType.values.sum())
            assertEquals(mapOf("kotlin.class" to 2L), info.symbolsByKind)
            assertEquals(
                3,
                info.diagnostics,
                "The missing build model, one note about the assumed dependencies and one about the missing history",
            )
            assertNotEquals("unknown", info.toolVersion)
            assertTrue(info.indexLocation.endsWith("/.kscope"))
        }
    }

    @Test
    fun `a second scan skips everything and a rebuild produces everything`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            service.scan()
            val second = service.scan()
            assertEquals(0, second.unitsProduced)
            assertEquals(scanUnits, second.unitsSkipped)
            val rebuilt = service.scan(rebuild = true)
            assertEquals(scanUnits, rebuilt.unitsProduced)
            assertEquals(0, rebuilt.unitsSkipped)
        }
    }

    @Test
    fun `verification compares the update with a clean rebuild`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            val verification = assertNotNull(service.scan(verify = true).verification)
            assertTrue(verification.matches)
            assertEquals(0, verification.differingUnits)
            val afterRebuild = assertNotNull(service.scan(rebuild = true, verify = true).verification)
            assertTrue(afterRebuild.matches)
        }
    }

    @Test
    fun `the index directory can be placed elsewhere`() = inWorkspace { root ->
        val index = Files.createTempDirectory("kscope-service-index")
        try {
            KrosScope.open(root, OpenOptions(indexDirectory = index)).use { service ->
                service.scan()
                assertTrue(service.info().indexLocation.startsWith(index.toString().replace('\\', '/')))
            }
            assertTrue(Files.isRegularFile(index.resolve("index.db")))
            assertFalse(Files.exists(root.resolve(".kscope")))
        } finally {
            index.toFile().deleteRecursively()
        }
    }

    @Test
    fun `results round trip through their json form`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            val scan = service.scan(verify = true)
            val info = service.info()
            val json = Json { ignoreUnknownKeys = true }
            assertEquals(scan, json.decodeFromString(ScanResult.serializer(), ServiceJson.encode(scan)))
            assertEquals(info, json.decodeFromString(InfoResult.serializer(), ServiceJson.encode(info)))
        }
    }
}
