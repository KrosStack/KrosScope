package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.runKscope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeMapOptionTest {
    @Test
    fun `map is an opt in foreground option that the help describes`() {
        val help = runKscope("serve", "--help")

        assertEquals(0, help.statusCode)
        assertTrue(help.stdout.contains("--map"), help.stdout)
        assertTrue(help.stdout.contains("random token"), help.stdout)
    }

    @Test
    fun `map cannot be combined with stop or status`() {
        for (action in listOf("--status", "--stop")) {
            val answer = runKscope("serve", "--map", action)

            assertEquals(2, answer.statusCode, action)
            assertTrue(answer.output.contains("--map requires foreground serving."), answer.output)
        }
    }
}
