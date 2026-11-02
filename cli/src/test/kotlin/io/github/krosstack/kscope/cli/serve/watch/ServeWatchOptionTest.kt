package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.runKscope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeWatchOptionTest {
    @Test
    fun `watch is an opt in foreground option`() {
        val help = runKscope("serve", "--help")
        assertEquals(0, help.statusCode)
        assertTrue(help.stdout.contains("--watch"))
        for (action in listOf("--status", "--stop")) {
            assertEquals(2, runKscope("serve", "--watch", action).statusCode)
        }
    }
}
