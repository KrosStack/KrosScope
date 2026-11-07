package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.doctor
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class WorkerDoctorTest {
    @Test
    fun `doctor reports component contention within its acquisition bound`(@TempDir root: Path) {
        val directory = ComponentDirectory(root.resolve("components"))
        KrosScope.open(root).use { service ->
            val pending = FutureTask {
                service.doctor(directory).checks.single { it.name == "semantic-worker" }.detail
            }
            val testing = Thread(pending).apply { isDaemon = true }
            val bound = WorkerSelfTest.LOCK_TIMEOUT_MILLIS + 2000
            try {
                directory.exclusively("semantic") {
                    val started = System.nanoTime()
                    testing.start()
                    val line = pending.get(bound, TimeUnit.MILLISECONDS)
                    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                    assertTrue(line == "Self-test skipped: component busy" && elapsed < bound)
                }
            } finally {
                testing.join(TimeUnit.SECONDS.toMillis(10))
            }
        }
    }
}
