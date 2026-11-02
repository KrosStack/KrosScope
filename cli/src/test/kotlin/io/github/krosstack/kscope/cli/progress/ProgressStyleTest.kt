package io.github.krosstack.kscope.cli.progress

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressStyleTest {
    @Test
    fun `auto follows the stream and never and quiet show nothing`() {
        val styles = listOf(ProgressMode.AUTO, ProgressMode.ALWAYS, ProgressMode.NEVER).flatMap { mode ->
            listOf(false, true).flatMap { quiet ->
                listOf(false, true).map { console ->
                    Triple(mode, quiet, console) to ProgressStyle.choose(mode, quiet, console)
                }
            }
        }.toMap()

        assertEquals(ProgressStyle.LIVE, styles.getValue(Triple(ProgressMode.AUTO, false, true)))
        assertEquals(ProgressStyle.PLAIN, styles.getValue(Triple(ProgressMode.AUTO, false, false)))
        assertEquals(ProgressStyle.LIVE, styles.getValue(Triple(ProgressMode.ALWAYS, false, false)))
        assertEquals(ProgressStyle.LIVE, styles.getValue(Triple(ProgressMode.ALWAYS, false, true)))
        assertEquals(ProgressStyle.OFF, styles.getValue(Triple(ProgressMode.NEVER, false, true)))
        assertEquals(ProgressStyle.OFF, styles.getValue(Triple(ProgressMode.NEVER, false, false)))
        ProgressMode.entries.forEach { mode ->
            assertEquals(ProgressStyle.OFF, styles.getValue(Triple(mode, true, true)), "quiet with $mode")
            assertEquals(ProgressStyle.OFF, styles.getValue(Triple(mode, true, false)), "quiet with $mode")
        }
    }
}
