package io.github.krosstack.kscope.service.settings

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsFileSafetyTest {
    private val fixture = SettingsFileFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    @Test
    fun `a file of exactly 64 KiB is read and one byte more is refused`() {
        val exactly = "#".repeat(MAXIMUM - 1) + "\n"
        assertEquals(MAXIMUM, exactly.toByteArray().size)
        assertEquals(loaded(ResourceSettings()), fixture.read(exactly))

        val issue = fixture.issuesOf(exactly + "\n").single()

        assertEquals(SettingsIssue(null, null, "must be at most 64 KiB"), issue)
        assertEquals("kscope.toml: must be at most 64 KiB", issue.describe("kscope.toml"))
    }

    @Test
    fun `a file that is not utf 8 is refused`() {
        Files.write(fixture.file, byteArrayOf(0x66, 0x6F, 0x72, 0x6D, 0x61, 0x74, 0xC3.toByte(), 0x28))

        assertEquals(listOf(SettingsIssue(null, null, "must be UTF-8 text")), fixture.issuesOfFile())
    }

    @Test
    fun `a directory of that name is refused`() {
        Files.createDirectory(fixture.file)

        assertEquals(listOf(SettingsIssue(null, null, "must be a regular file")), fixture.issuesOfFile())
    }

    @Test
    fun `a symbolic link is refused and its target is not read`() {
        val target = Files.writeString(fixture.directory.resolve("elsewhere.toml"), "format = 1\n")
        linkOrSkip(fixture.file, target)

        assertEquals(listOf(SettingsIssue(null, null, "must not be a symbolic link")), fixture.issuesOfFile())
    }

    @Test
    fun `a link to nothing is refused as a link`() {
        linkOrSkip(fixture.file, fixture.directory.resolve("missing.toml"))

        assertEquals(listOf(SettingsIssue(null, null, "must not be a symbolic link")), fixture.issuesOfFile())
    }

    @Test
    fun `nothing in the file is executed expanded or followed`() {
        val marker = fixture.directory.resolve("marker").toString().replace('\\', '/')
        val text = "format = 1\n[resources]\nworkers = \"\$(touch $marker)\"\nworker-heap-mib = \"file:///\${HOME}\"\n"

        val issues = fixture.issuesOf(text)

        assertEquals(listOf("resources.workers", "resources.worker-heap-mib"), issues.map { it.key })
        assertTrue(Files.notExists(Path.of(marker)))
    }

    private fun linkOrSkip(link: Path, target: Path) {
        val created = try {
            Files.createSymbolicLink(link, target)
            true
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
        assumeTrue(created, "this system does not let the test create symbolic links")
    }

    private companion object {
        const val MAXIMUM = 64 * 1024
    }
}
