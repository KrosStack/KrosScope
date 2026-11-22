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

class GitHeadLayoutsTest {
    private val fixture = HeadFixture()

    @BeforeTest
    fun createCheckout() {
        assumeTrue(TestGit.available())
        fixture.create()
    }

    @AfterTest
    fun cleanUp() {
        fixture.delete()
    }

    @Test
    fun `a reftable folder gives no commit although a reference file is there`() {
        assertTrue(Files.isRegularFile(fixture.checkout.resolve(".git").resolve(fixture.branch())))
        Files.createDirectories(fixture.checkout.resolve(".git/reftable"))

        assertNull(GitHeadCommit.of(fixture.checkout))
    }

    @Test
    fun `a reference storage extension in the config gives no commit although a reference file is there`() {
        assertTrue(Files.isRegularFile(fixture.checkout.resolve(".git").resolve(fixture.branch())))
        val extension = "[core]\n\tbare = false\n[Extensions]\n\trefStorage = reftable\n"
        Files.writeString(fixture.checkout.resolve(".git/config"), extension, StandardOpenOption.APPEND)

        assertNull(GitHeadCommit.of(fixture.checkout))
    }

    @Test
    fun `a sixty-four digit object id is read as it is`() {
        val id = "0123456789abcdef".repeat(4)
        Files.writeString(fixture.checkout.resolve(".git/HEAD"), "$id\n")

        assertEquals(id, GitHeadCommit.of(fixture.checkout))
    }
}
