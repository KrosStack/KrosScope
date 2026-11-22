package io.github.krosstack.kscope.engine.structure

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHeadPackedReferencesTest {
    private val fixture = HeadFixture()

    @BeforeTest
    fun createCheckout() {
        assumeTrue(TestGit.available())
        fixture.create()
        TestGit.output(fixture.checkout, "pack-refs", "--all")
    }

    @AfterTest
    fun cleanUp() {
        fixture.delete()
    }

    @Test
    fun `a large packed references file is read only up to the match`() {
        val commit = fixture.head()
        val filler = (1..LARGE).joinToString("\n", prefix = "\n") { "$commit refs/heads/z$it" }
        Files.writeString(fixture.checkout.resolve(".git/packed-refs"), filler, StandardOpenOption.APPEND)
        var checks = 0

        val found = GitHeadCommit.of(fixture.checkout) {
            checks++
            false
        }

        assertEquals(commit, found)
        assertTrue(checks < EARLY, "$checks lines were looked at")
    }

    @Test
    fun `a packed references file is not read when the time is over`() {
        assertNull(GitHeadCommit.of(fixture.checkout) { true })
    }

    private companion object {
        const val LARGE = 200_000
        const val EARLY = 10
    }
}
