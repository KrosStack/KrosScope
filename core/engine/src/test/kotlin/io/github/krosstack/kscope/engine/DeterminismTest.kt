package io.github.krosstack.kscope.engine

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DeterminismTest {
    private fun dumpOf(sample: String): String = TestWorkspace(sample).use { workspace ->
        workspace.seedRepositoryId("fixed-repository-id")
        workspace.engine().use { engine ->
            engine.update()
            engine.canonicalDump()
        }
    }

    @Test
    fun `two scans of the same tree into fresh stores serialize identically`() {
        assertEquals(dumpOf("gradle-multi"), dumpOf("gradle-multi"))
    }

    @Test
    fun `a rebuild serializes like the scan it replaces`() = TestWorkspace("gradle-multi").use { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            val dump = engine.canonicalDump()
            engine.rebuild()
            assertEquals(dump, engine.canonicalDump())
        }
    }

    @Test
    fun `no fact holds an absolute path a timestamp or a host name`() = TestWorkspace("gradle-multi").use { workspace ->
        workspace.engine(coreRegistry(RecordingProducer("recorder"), RecordingLinker("linker"))).use { engine ->
            engine.update()
            val dump = engine.canonicalDump()
            val forbidden = listOf(
                workspace.root.toString(),
                workspace.root.toString().replace('\\', '/'),
                workspace.indexDirectory.toString(),
                System.getProperty("java.io.tmpdir"),
                System.getProperty("user.home"),
                System.getProperty("user.name"),
                hostName(),
            ).filter { it.length >= MIN_DISTINCTIVE_LENGTH }
            forbidden.forEach { assertFalse(dump.contains(it), "The dump contains '$it'") }
            assertFalse(drivePath.containsMatchIn(dump), "The dump contains a drive path")
            assertFalse(timestamp.containsMatchIn(dump), "The dump contains a timestamp")
            assertFalse(dump.contains("\\\\"), "The dump contains a backslash")
        }
    }

    private fun hostName(): String = try {
        InetAddress.getLocalHost().hostName
    } catch (_: UnknownHostException) {
        System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: ""
    }

    private companion object {
        const val MIN_DISTINCTIVE_LENGTH = 5
        val drivePath = Regex("""[A-Za-z]:[\\/]""")
        val timestamp = Regex("""\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}""")
    }
}
