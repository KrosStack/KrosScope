package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLocator
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class KotlinApiDigesterProcessTest {
    @Test
    fun `a real worker ignores block bodies and retains signatures`(@TempDir root: Path) {
        val fixture = ApiDigestFixture(root)
        val first = fixture.source("First.kt", "fun f(x: Int): Int { return x + 1 }")
        val body = fixture.source("Body.kt", "fun f(x: Int): Int { return x + 2 }")
        val signature = fixture.source("Signature.kt", "fun f(x: Long): Int { return 1 }")
        val digester = KotlinApiDigester()
        val digests = digester.digest(listOf(first, body, signature), fixture).digests
        assertEquals(3, digests.size)
        assertEquals(digests.getValue(first.ref), digests.getValue(body.ref))
        assertNotEquals(digests.getValue(first.ref), digests.getValue(signature.ref))
        assertEquals("1", digester.version)
        assertEquals(setOf(ArtifactType("kotlin.source", ArtifactCategory.SOURCE)), digester.consumes)
        assertNotNull(KotlinAnalyzerProducer().apiDigester)
    }

    @Test
    fun `more than five hundred sources are answered on one worker`(@TempDir root: Path) {
        val fixture = ApiDigestFixture(root)
        val sources = (0..500).map { fixture.source("File$it.kt", "fun f$it() {}") }
        var starts = 0
        val launcher = WorkerLauncher({
            starts++
            WorkerLocator.system().command()
        })
        val digests = KotlinApiDigester(launcher) {}.digest(sources, fixture)
        assertEquals(sources.map { it.ref }.toSet(), digests.digests.keys)
        assertEquals(emptySet(), digests.withoutDigest)
        assertEquals(1, starts)
    }

    @Test
    fun `a source that does not parse is examined and has no digest`(@TempDir root: Path) {
        val fixture = ApiDigestFixture(root)
        val valid = fixture.source("Valid.kt", "fun f() {}")
        val broken = fixture.source("Broken.kt", "fun broken(")

        val digests = KotlinApiDigester().digest(listOf(valid, broken), fixture)

        assertEquals(setOf(valid.ref), digests.digests.keys)
        assertEquals(setOf(broken.ref), digests.withoutDigest)
    }
}
