package io.github.krosstack.kscope.analyzer.history

import java.nio.file.AccessDeniedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class TemporaryTreeTest {
    @Test
    fun `a file that vanished during traversal is tolerated`() {
        val missing = Path.of("vanished.lock")
        assertEquals(FileVisitResult.CONTINUE, TemporaryTree.visitFileFailed(missing, NoSuchFileException("vanished")))
    }

    @Test
    fun `other traversal failures are propagated unchanged`() {
        val denied = Path.of("denied.lock")
        val error = AccessDeniedException("denied")
        assertSame(error, assertFailsWith<AccessDeniedException> { TemporaryTree.visitFileFailed(denied, error) })
    }

    @Test
    fun `cleanup deletes nested contents and tolerates an already deleted repository`() {
        val root = Files.createTempDirectory("kscope-cleanup")
        try {
            val objects = Files.createDirectories(root.resolve(".git/objects"))
            Files.writeString(objects.resolve("maintenance.lock"), "lock")
            TemporaryTree.delete(root)
            assertFalse(Files.exists(root))
            TemporaryTree.delete(root)
        } finally {
            TemporaryTree.delete(root)
        }
    }
}
