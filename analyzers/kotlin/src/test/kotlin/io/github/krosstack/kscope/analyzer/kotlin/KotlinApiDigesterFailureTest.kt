package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.process.FakeWorkerHarness
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerCommand
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
import io.github.krosstack.kscope.spi.ApiDigests
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinApiDigesterFailureTest {
    @Test
    fun `an empty list starts no worker`(@TempDir root: Path) {
        var starts = 0
        val launcher = WorkerLauncher({
            starts++
            WorkerCommand.Missing("unavailable")
        })
        assertEquals(ApiDigests.NONE, KotlinApiDigester(launcher) {}.digest(emptyList(), ApiDigestFixture(root)))
        assertEquals(0, starts)
    }

    @Test
    fun `an unavailable worker examines nothing`(@TempDir root: Path) {
        val fixture = ApiDigestFixture(root)
        val source = fixture.source("Api.kt", "fun f() {}")
        val launcher = WorkerLauncher({ WorkerCommand.Missing("unavailable") })
        assertEquals(ApiDigests.NONE, KotlinApiDigester(launcher) {}.digest(listOf(source), fixture))
    }

    @Test
    fun `a worker that exits leaves unanswered sources unexamined`(@TempDir root: Path) {
        val fixture = ApiDigestFixture(root)
        val source = fixture.source("Api.kt", "fun f() {}")
        FakeWorkerHarness().use { worker ->
            val digester = KotlinApiDigester(worker.launcher("exit")) {}
            assertEquals(ApiDigests.NONE, digester.digest(listOf(source), fixture))
        }
    }

    @Test
    fun `a worker failure preserves digests already answered`(@TempDir root: Path) {
        val fixture = ApiDigestFixture(root)
        val first = fixture.source("First.kt", "fun f() {}")
        val second = fixture.source("Second.kt", "fun g() {}")
        FakeWorkerHarness().use { worker ->
            val digester = KotlinApiDigester(worker.launcher("fail")) {}
            val expected = ApiDigests(mapOf(first.ref to "ab".repeat(32)), emptySet())
            assertEquals(expected, digester.digest(listOf(first, second), fixture))
        }
    }
}
