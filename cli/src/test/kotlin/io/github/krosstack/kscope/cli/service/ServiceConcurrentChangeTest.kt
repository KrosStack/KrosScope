package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceFileReading
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceConcurrentChangeTest {
    @Test
    fun `many adds from several threads leave every workspace in the file`() = inWorkspace { parent ->
        inServiceHome { home ->
            val directories = (1..DIRECTORIES).map { Files.createDirectory(parent.resolve("w$it")) }
            val executor = Executors.newFixedThreadPool(THREADS)
            val start = CountDownLatch(1)
            try {
                val results = directories.map { directory ->
                    executor.submit<CliktCommandTestResult> {
                        start.await()
                        runService(home, "add", "--workspace", directory.toString())
                    }
                }
                start.countDown()
                results.forEach { assertEquals(0, it.get(WAIT_SECONDS, TimeUnit.SECONDS).statusCode) }
            } finally {
                executor.shutdownNow()
            }

            val kept = (ServiceFile(home.resolve("service.toml")).read() as ServiceFileReading.Read).workspaces
            assertEquals(directories.map { WorkspacePaths.key(WorkspacePaths.real(it)) }.sorted(), kept)
        }
    }

    private companion object {
        const val DIRECTORIES = 24
        const val THREADS = 8
        const val WAIT_SECONDS = 120L
    }
}
