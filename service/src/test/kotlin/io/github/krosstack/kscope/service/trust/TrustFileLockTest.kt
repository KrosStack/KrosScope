package io.github.krosstack.kscope.service.trust

import io.github.krosstack.kscope.service.TemporaryTree
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustFileLockTest {
    private val base: Path = Files.createTempDirectory("kscope-trust-lock")
    private val lockFile = base.resolve("home").resolve("trust.lock")

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(base)
    }

    private fun holdsFileLock(): Boolean = try {
        FileChannel.open(lockFile, StandardOpenOption.WRITE).use { it.tryLock()?.release() }
        false
    } catch (_: OverlappingFileLockException) {
        true
    }

    @Test
    fun `the block runs while the lock file is locked and the lock is released afterwards`() {
        val during = TrustFileLock.exclusively(lockFile) { holdsFileLock() }

        assertTrue(during)
        assertTrue(Files.exists(lockFile))
        assertFalse(holdsFileLock())
    }

    @Test
    fun `the lock is released when the block fails`() {
        assertFailsWith<IllegalStateException> { TrustFileLock.exclusively(lockFile) { error("failed") } }

        assertFalse(holdsFileLock())
        assertEquals("again", TrustFileLock.exclusively(lockFile) { "again" })
    }

    @Test
    fun `threads of one process take the lock one after the other`() {
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit {
                TrustFileLock.exclusively(lockFile) {
                    events += "first in"
                    inside.countDown()
                    release.await()
                    events += "first out"
                }
            }
            inside.await()
            val second = pool.submit { TrustFileLock.exclusively(lockFile) { events += "second in" } }
            release.countDown()
            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        assertEquals(listOf("first in", "first out", "second in"), events)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 60L
    }
}
