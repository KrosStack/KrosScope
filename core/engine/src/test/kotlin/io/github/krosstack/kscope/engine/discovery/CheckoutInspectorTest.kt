package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.CheckoutInspection
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckoutInspectorTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `a directory inside a work tree with a commit names the top of the work tree`() =
        TestWorkspace().use { workspace ->
            val committed = repositoryAt(workspace.root.resolve("committed"))
            Files.createDirectories(committed.resolve("src/main"))
            val inspection = CheckoutInspection.of(committed.resolve("src/main"))
            assertTrue(inspection is CheckoutInspection.Committed, "$inspection")
            assertEquals(committed.toRealPath(), inspection.root.toRealPath())
        }

    @Test
    fun `a work tree without a commit and a plain directory are told apart`() = TestWorkspace().use { workspace ->
        val empty = repositoryAt(workspace.root.resolve("empty"), commit = false)
        assertTrue(CheckoutInspection.of(empty) is CheckoutInspection.WithoutCommit)
        Files.createDirectories(workspace.root.resolve("plain"))
        assertEquals(CheckoutInspection.NotARepository, CheckoutInspection.of(workspace.root.resolve("plain")))
    }
}
