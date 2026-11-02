package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServeServicesReleaseTest {
    private val unchanged = ScanResult(0, 0, 0, 0, DiagnosticSummary(0, 0, 0, emptyList()), null, emptyMap(), "")
    private val fakeScan = ScanRunner { _, _, _, _ -> unchanged }

    private fun <T> warmServices(
        root: Path,
        changes: MutableList<Boolean> = mutableListOf(),
        block: (ServeServices) -> T,
    ): T {
        val services = ServeServices(root, fakeScan, onLoadChange = { changes.add(it) })
        try {
            services.warm()
            return block(services)
        } finally {
            services.close()
        }
    }

    @Test
    fun `a released workspace is not loaded and a query loads it and answers as before`() = inScannedWorkspace { root ->
        warmServices(root) { services ->
            val before = services.use { it.graphView() }
            val info = services.use { it.info() }
            assertTrue(services.loaded)

            services.release()

            assertFalse(services.loaded)
            assertEquals(before, services.use { it.graphView() })
            assertEquals(info, services.use { it.info() })
            assertTrue(services.loaded)
        }
    }

    @Test
    fun `a change of what is in memory is reported once, and never for a workspace that was opened`() =
        inScannedWorkspace { root ->
            val changes = mutableListOf<Boolean>()
            warmServices(root, changes) { services ->
                assertEquals(emptyList(), changes)

                services.release()
                services.release()
                services.use { it.info() }
                services.use { it.info() }

                assertEquals(listOf(false, true), changes)
            }
        }

    @Test
    fun `a scan of a released workspace leaves it released and a later query loads it`() = inScannedWorkspace { root ->
        warmServices(root) { services ->
            val before = services.use { it.graphView() }
            services.release()

            services.scan(OpenOptions(), rebuild = false, verify = false)

            assertFalse(services.loaded)
            assertEquals(before, services.use { it.graphView() })
            assertTrue(services.loaded)
        }
    }

    @Test
    fun `a scan of a loaded workspace warms it again as before`() = inScannedWorkspace { root ->
        warmServices(root) { services ->
            services.scan(OpenOptions(), rebuild = false, verify = false)

            assertTrue(services.loaded)
        }
    }
}
