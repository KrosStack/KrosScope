package io.github.krosstack.kscope.service.freshness

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartedProcessesTest {
    @Test
    fun `a process is left to run until the check stops and is ended then`() {
        val started = StartedProcesses()
        val process = RecordedProcess()

        started.add(process)
        assertFalse(process.ended)
        started.stop()

        assertTrue(process.ended)
    }

    @Test
    fun `a process that starts after the check stopped is ended at once`() {
        val started = StartedProcesses()
        val process = RecordedProcess()

        started.stop()
        started.add(process)

        assertTrue(process.ended)
    }
}
