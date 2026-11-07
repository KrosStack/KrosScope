package io.github.krosstack.kscope.service.components

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class ComponentMutationTest : ComponentInstallerFixture() {
    @Test
    fun `install waits for another installer to finish its active transfer`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val entry = entry(zip("data")).let { it.copy(files = it.files.takeLast(1)) }
        val firstInstaller = ComponentInstaller(ComponentDirectory(root)) {
            entered.countDown()
            check(release.await(WAIT_SECONDS, TimeUnit.SECONDS))
            ByteArrayInputStream(plain)
        }
        val secondInstaller = ComponentInstaller(ComponentDirectory(root)) { ByteArrayInputStream(plain) }
        val first = FutureTask { firstInstaller.install(entry, "linux-x64", InstallSource.Network) }
        val second = FutureTask {
            secondInstaller.install(entry.copy(version = "2"), "linux-x64", InstallSource.Network)
        }
        val firstThread = Thread(first)
        val secondThread = Thread(second)
        try {
            firstThread.start()
            check(entered.await(WAIT_SECONDS, TimeUnit.SECONDS))
            secondThread.start()
            awaitLockWait(secondThread)
            release.countDown()
            val result = first.get(WAIT_SECONDS, TimeUnit.SECONDS)
            second.get(WAIT_SECONDS, TimeUnit.SECONDS)
            assertTrue(
                result is InstallResult.Installed &&
                    Files.readString(root.resolve("sample/1/LICENSE")) == "component licence" &&
                    Files.readString(root.resolve("sample/2/LICENSE")) == "component licence",
            )
        } finally {
            release.countDown()
            firstThread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
            secondThread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
        }
    }

    private fun awaitLockWait(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (
            thread.state != Thread.State.WAITING ||
            thread.stackTrace.none { it.className.contains("ReentrantLock") }
        ) {
            check(thread.isAlive && System.nanoTime() < deadline) { "Installer did not wait for the component lock" }
            Thread.onSpinWait()
        }
    }

    private companion object {
        const val WAIT_SECONDS = 10L
    }
}
