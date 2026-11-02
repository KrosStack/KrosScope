package io.github.krosstack.kscope.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class SingleRepositoryDumpTest {
    private val withDirectory = "\"originUrl\":null,\"directory\":\"\""
    private val withoutDirectory = "\"originUrl\":null"

    private fun dumpOf(sample: String): String = TestWorkspace(sample).use { workspace ->
        workspace.seedRepositoryId("fixed-repository-id")
        workspace.engine().use { engine ->
            engine.update()
            engine.canonicalDump()
        }
    }

    private val discoveryFingerprint = Regex(
        """("id":"kscope\.discovery","version":"1"\},"owner":\{[^}]*\},"inputFingerprint":")[0-9a-f]+(")""",
    )

    private fun withoutDiscoveryFingerprint(dump: String) = dump.replace(discoveryFingerprint, "$1masked$2")

    private fun golden(name: String): String =
        checkNotNull(SingleRepositoryDumpTest::class.java.getResource("/golden/$name")) { "Missing golden $name" }
            .readText()

    private fun assertDumpUnchanged(sample: String) {
        val dump = dumpOf(sample)
        assertEquals(1, dump.windowed(withDirectory.length).count { it == withDirectory })
        val unchanged = withoutDiscoveryFingerprint(dump.replace(withDirectory, withoutDirectory))
        assertEquals(withoutDiscoveryFingerprint(golden("$sample-single-repository.json")), unchanged)
    }

    @Test
    fun `a workspace of one repository dumps as before except for the empty directory of the repository`() =
        assertDumpUnchanged("gradle-multi")

    @Test
    fun `a plain directory without build files dumps as before except for the empty directory of the repository`() =
        assertDumpUnchanged("plain")

    @Test
    fun `a workspace of one repository has no coordinate batch`() {
        val dump = dumpOf("gradle-multi")
        assertEquals(false, "kscope.engine.coordinates" in dump)
        assertEquals(false, "maven-coordinates" in dump)
    }

    @Test
    fun `a workspace of one repository has no JAR link batch`() {
        val dump = dumpOf("gradle-multi")
        assertEquals(false, "kscope.engine.jvm-libraries" in dump)
        assertEquals(false, "jvm-library" in dump)
    }
}
