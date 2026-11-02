package io.github.krosstack.kscope.service.trust

import io.github.krosstack.kscope.service.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustStoreTest {
    private val base: Path = Files.createTempDirectory("kscope-trust")
    private val file = base.resolve("home").resolve(TrustStore.FILE_NAME)
    private val store = TrustStore(file)
    private val workspace: Path = Files.createDirectories(base.resolve("workspace"))

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(base)
    }

    @Test
    fun `nothing is trusted until the user trusts a workspace`() {
        assertFalse(store.isTrusted(workspace))
        assertFalse(Files.exists(file))
    }

    @Test
    fun `granting trusts the workspace and is remembered by a new store`() {
        assertTrue(store.grant(workspace))
        assertFalse(store.grant(workspace))

        assertTrue(store.isTrusted(workspace))
        assertTrue(TrustStore(file).isTrusted(workspace))
    }

    @Test
    fun `revoking removes only that workspace`() {
        val other = Files.createDirectories(base.resolve("other"))
        store.grant(workspace)
        store.grant(other)

        assertTrue(store.revoke(workspace))
        assertFalse(store.revoke(workspace))

        assertFalse(store.isTrusted(workspace))
        assertTrue(store.isTrusted(other))
    }

    @Test
    fun `trust is keyed by the canonical path however the workspace is reached`() {
        store.grant(workspace)

        assertTrue(store.isTrusted(base.resolve("workspace").resolve("..").resolve("workspace")))
        assertTrue(store.isTrusted(workspace.resolve(".")))
        assertFalse(store.isTrusted(workspace.resolve("nested")))
        assertFalse(store.isTrusted(base))
    }

    @Test
    fun `a file that cannot be read trusts nothing and is not overwritten`() {
        Files.createDirectories(file.parent)
        Files.writeString(file, "trusted-workspaces = [\n")

        assertFalse(store.isTrusted(workspace))
        assertFailsWith<TrustStoreException> { store.grant(workspace) }
        assertFailsWith<TrustStoreException> { store.revoke(workspace) }
        assertEquals("trusted-workspaces = [\n", Files.readString(file))
    }

    @Test
    fun `a file with other keys or other value types trusts nothing`() {
        Files.createDirectories(file.parent)
        val key = TrustFileText.KEY
        for (text in listOf("other = 1\n", "$key = \"x\"\n", "$key = [1]\n", "$key = []\nextra = true\n")) {
            Files.writeString(file, text)
            assertFalse(store.isTrusted(workspace), text)
        }
    }

    @Test
    fun `a workspace cannot grant itself trust by what it contains`() {
        val own = "trusted-workspaces = [\"${WorkspaceKey.of(workspace)}\"]\n"
        Files.writeString(workspace.resolve("kscope.toml"), own + "level = \"exact\"\n")
        Files.writeString(workspace.resolve(TrustStore.FILE_NAME), own)

        assertFalse(store.isTrusted(workspace))
    }

    @Test
    fun `the file lists the workspaces in a stable order and keeps awkward paths`() {
        val awkward = Files.createDirectories(base.resolve("a name, with [odd] ; chars"))
        store.grant(workspace)
        store.grant(awkward)

        val first = Files.readString(file)
        store.revoke(workspace)
        store.grant(workspace)

        assertTrue(store.isTrusted(awkward))
        assertTrue(first.startsWith("trusted-workspaces = [\n"), first)
        assertEquals(first, Files.readString(file))
    }

    @Test
    fun `grants and revocations that run at the same time lose none of each other's changes`() {
        val revoked = (0 until CONCURRENT_PAIRS).map { Files.createDirectories(base.resolve("revoked-$it")) }
        val granted = (0 until CONCURRENT_PAIRS).map { Files.createDirectories(base.resolve("granted-$it")) }
        revoked.forEach(store::grant)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(CONCURRENT_PAIRS * 2)
        try {
            val changes = revoked.map { path -> pool.submit(afterStart(start) { TrustStore(file).revoke(path) }) } +
                granted.map { path -> pool.submit(afterStart(start) { TrustStore(file).grant(path) }) }
            start.countDown()
            changes.forEach { assertTrue(it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) }
        } finally {
            pool.shutdownNow()
        }

        assertTrue(revoked.none(store::isTrusted), "a revoked workspace was written back")
        assertTrue(granted.all(store::isTrusted), "a granted workspace was lost")
    }

    private fun afterStart(start: CountDownLatch, change: () -> Boolean) = Callable {
        start.await()
        change()
    }

    private companion object {
        const val CONCURRENT_PAIRS = 12
        const val TIMEOUT_SECONDS = 60L
    }
}
