package io.github.krosstack.kscope.analyzer.kotlin.worker.digest

import io.github.krosstack.kscope.analyzer.kotlin.worker.ResponseWriter
import io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerService
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiDigestRequestTest {
    @Test
    fun `invalid scripts Java and unreadable sources are answered without a digest`(@TempDir root: Path) {
        val fixture = DigestFixture(root)
        val valid = fixture.file("Valid.kt", "fun f() {}")
        val sources = listOf(
            fixture.file("Broken.kt", "fun broken("),
            fixture.file("Build.kts", "println(1)"),
            fixture.file("Java.java", "class Java {}"),
            fixture.file("Missing.kt"),
            fixture.file("InvalidPath.kt").copy(absolutePath = "\u0000"),
            valid,
        )
        val responses = fixture.responses(sources)
        val answers = responses.filterIsInstance<WorkerResponse.ApiDigest>()
        assertEquals(sources.map { it.ref }, answers.map { it.ref })
        assertEquals(listOf(valid.ref), answers.filter { it.digest != null }.map { it.ref })
        assertEquals(WorkerResponse.Completed("d-1"), responses.last())
        assertEquals(sources.size + 1, responses.size)
    }

    @Test
    fun `an upper case extension is a Kotlin source like any other`(@TempDir root: Path) {
        val fixture = DigestFixture(root)
        val text = "package api\nfun f(): Int { return 1 }\n"
        val lower = fixture.file("Lower.kt", text)
        val upper = fixture.file("Upper.KT", text)

        val digests = fixture.digests(listOf(lower, upper))

        assertEquals(setOf(lower.ref, upper.ref), digests.keys)
        assertEquals(digests.getValue(lower.ref), digests.getValue(upper.ref))
    }

    @Test
    fun `the digest is lowercase SHA-256 of UTF-8 API text`(@TempDir root: Path) {
        val fixture = DigestFixture(root)
        val source = fixture.file("Unicode.kt", "fun café() {}")
        val expected = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest("\n\nfun\ncafé\n(\n)\n{}\n".toByteArray(Charsets.UTF_8)),
        )
        assertEquals(expected, fixture.digests(listOf(source)).getValue(source.ref))
    }

    @Test
    fun `line separators are whitespace and do not prevent a digest`(@TempDir root: Path) {
        val fixture = DigestFixture(root)
        val text = "package api\n\nfun f() {\n    println(1)\n}\n"
        val unix = fixture.file("Unix.kt", text)
        val windows = fixture.file("Windows.kt", text.replace("\n", "\r\n"))
        val digests = fixture.digests(listOf(unix, windows))
        assertEquals(setOf(unix.ref, windows.ref), digests.keys)
        assertEquals(digests.getValue(unix.ref), digests.getValue(windows.ref))
    }

    @Test
    fun `the handshake advertises protocol four and API digests`() {
        val ready = WorkerService(ResponseWriter(StringWriter())).ready()
        assertEquals(4, ready.protocolVersion)
        assertTrue(AnalyzerCapability.API_DIGEST in ready.capabilities)
    }
}
