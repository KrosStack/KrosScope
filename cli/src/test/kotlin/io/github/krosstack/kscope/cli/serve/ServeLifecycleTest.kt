package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.trust.MachineDirectory
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServeLifecycleTest {
    @Test
    fun `lock metadata status and stop follow the server lifetime`() = inScannedWorkspace { root ->
        val file = root.resolve(".kscope/serve.json")
        assertEquals(1, runKscope("serve", "--status", "--workspace", root.toString()).statusCode)
        withTestServer(root) { server ->
            assertEquals("127.0.0.1", server.boundAddress.hostAddress)
            val metadata = assertNotNull(ServeMetadata.read(root))
            assertEquals(ProcessHandle.current().pid(), metadata.pid)
            assertEquals(KrosScope.toolVersion(), metadata.toolVersion)
            assertEquals(1, metadata.protocol)
            assertEquals(64, metadata.token.length)
            assertTrue(metadata.startTime.isNotBlank())
            assertFalse(Files.list(file.parent).use { paths -> paths.anyMatch { it.toString().endsWith(".tmp") } })
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
                assertEquals(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file),
                )
            }
            assertFailsWith<ServerAlreadyRunning> { ServeServer.open(root) }
            val status = runKscope("serve", "--status", "--workspace", root.toString())
            assertEquals(0, status.statusCode)
            assertTrue(status.stdout.contains(metadata.pid.toString()))
            assertFalse(status.output.contains(metadata.token))
            assertEquals(0, runKscope("serve", "--stop", "--workspace", root.toString()).statusCode)
        }
        assertFalse(Files.exists(file))
        withTestServer(root) { assertTrue(Files.exists(file)) }
        assertEquals(0, runKscope("serve", "--stop", "--workspace", root.toString()).statusCode)
    }

    @Test
    fun `stale metadata falls back locally and can be replaced`() = inScannedWorkspace { root ->
        ServeMetadata(0, 0, "unused", "stale", 1, "then").write(root)
        val local = runKscope("info", "--workspace", root.toString(), "--no-server")
        val stale = runKscope("info", "--workspace", root.toString())
        assertEquals(local.stdout, stale.stdout)
        assertEquals("", stale.stderr)
        withTestServer(root) { assertEquals(0, runKscope("info", "--workspace", root.toString()).statusCode) }
    }

    @Test
    fun `idle timeout stops and cleans up with an injected clock`() = inScannedWorkspace { root ->
        val clock = AtomicLong()
        val server = ServeServer.open(root, ServeSettings(idleTimeoutMillis = 100, clock = clock::get))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val running = executor.submit { server.run() }
            clock.set(TimeUnit.SECONDS.toNanos(1))
            running.get(5, TimeUnit.SECONDS)
            assertFalse(Files.exists(root.resolve(".kscope/serve.json")))
        } finally {
            server.requestStop()
            executor.shutdownNow()
            server.close()
        }
    }

    @Test
    fun `stopping a server leaves the metadata of another server alone`() = inScannedWorkspace { root ->
        val other = ServeMetadata(1, 2, "another-token", "other", 1, "then")
        withTestServer(root) { other.write(root) }
        assertEquals(other, ServeMetadata.read(root))
    }

    @Test
    fun `a lock outside the index directory is held for as long as the server lives`() = inWorkspace { root ->
        val locks = MachineDirectory.resolve({ System.getProperty(it) }, { System.getenv(it) }).resolve("locks")
        val before = heldFiles(locks)
        ServeLock.acquire(root).use { assertEquals(before + 1, heldFiles(locks)) }
        assertEquals(before, heldFiles(locks))
    }

    @Test
    fun `a server does not start beside a running one when its lock file was deleted`() = inScannedWorkspace { root ->
        withTestServer(root) {
            val lock = root.resolve(".kscope/serve.lock")
            runCatching { Files.deleteIfExists(lock) }
            assumeTrue(Files.notExists(lock), "this file system keeps a file that is open from being deleted")
            assertFailsWith<ServerAlreadyRunning> { ServeServer.open(root) }
            Unit
        }
    }

    @Test
    fun `a lock file outside the workspace that cannot be opened leaves the workspace lock to guard the server`() {
        inWorkspace { root ->
            withServiceHome { home ->
                ServeLock.acquire(root).close()
                val outside = Files.list(home.resolve("locks")).use { files -> files.findFirst().get() }
                Files.delete(outside)
                Files.createDirectory(outside)
                ServeLock.acquire(root).use { assertFailsWith<ServerAlreadyRunning> { ServeLock.acquire(root) } }
            }
        }
    }

    @Test
    fun `a request is recorded as ended before its exit response is sent`() = inScannedWorkspace { root ->
        val clock = AtomicLong(TimeUnit.SECONDS.toNanos(1))
        val settings = ServeSettings(
            clock = clock::get,
            onRequest = { clock.set(TimeUnit.SECONDS.toNanos(2)) },
            beforeExitResponse = { clock.set(TimeUnit.SECONDS.toNanos(3)) },
        )
        val server = ServeServer.open(root, settings)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val running = executor.submit { server.run() }
            assertEquals(0, server.query(listOf("info", "--quiet")).exitCode)
            server.requestStop()
            running.get(5, TimeUnit.SECONDS)
            assertEquals(TimeUnit.SECONDS.toNanos(2), server.lastRequestAt)
        } finally {
            server.requestStop()
            executor.shutdownNow()
            server.close()
        }
    }

    private fun withServiceHome(block: (Path) -> Unit) {
        val home = Files.createTempDirectory("kscope-service-home")
        val previous = System.getProperty(MachineDirectory.PROPERTY)
        System.setProperty(MachineDirectory.PROPERTY, home.toString())
        try {
            block(home)
        } finally {
            if (previous == null) {
                System.clearProperty(MachineDirectory.PROPERTY)
            } else {
                System.setProperty(MachineDirectory.PROPERTY, previous)
            }
            home.toFile().deleteRecursively()
        }
    }

    private fun heldFiles(directory: Path): Int {
        if (!Files.isDirectory(directory)) return 0
        return Files.list(directory).use { files -> files.filter(::isHeld).count().toInt() }
    }

    private fun isHeld(file: Path): Boolean = FileChannel.open(file, StandardOpenOption.WRITE).use { channel ->
        val lock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        lock?.release()
        lock == null
    }
}
