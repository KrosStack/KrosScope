package io.github.krosstack.kscope.cli.serve.map

import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapEventsTest {
    private val states = MapStates()

    private class Recording(private val marker: String) : OutputStream() {
        private val text = StringBuilder()
        val seen = CountDownLatch(1)

        @Synchronized
        override fun write(byte: Int) {
            text.append(byte.toChar())
            if (text.contains(marker)) seen.countDown()
        }

        @Synchronized
        fun written(): String = text.toString()
    }

    @Test
    fun `a viewer gets the state at once and again when it changes`() {
        val output = Recording("\"version\":2")
        val viewer = thread { MapEvents(states).stream(output) }

        states.update { it.copy(version = 2) }

        assertTrue(output.seen.await(30, TimeUnit.SECONDS), output.written())
        states.close()
        viewer.join()
        val events = output.written().split("\n\n").filter { it.isNotEmpty() }
        assertTrue(events.all { it.startsWith("event: state\ndata: {") }, output.written())
        assertTrue(events.last().contains("\"version\":2"), output.written())
    }

    @Test
    fun `the state is sent with every field and an unknown one as null`() {
        val output = Recording("\n\n")
        val viewer = thread { MapEvents(states).stream(output) }

        assertTrue(output.seen.await(30, TimeUnit.SECONDS))
        states.close()
        viewer.join()

        assertEquals(
            "event: state\ndata: {\"version\":0,\"indexed\":false,\"level\":null,\"scanning\":false," +
                "\"watching\":false,\"watch\":null}\n\n",
            output.written(),
        )
    }

    @Test
    fun `a quiet stream gets a comment now and then`() {
        val output = Recording(": ping\n\n")
        val viewer = thread { MapEvents(states, pingMillis = 5).stream(output) }

        assertTrue(output.seen.await(30, TimeUnit.SECONDS), output.written())
        states.close()
        viewer.join()
    }

    @Test
    fun `a viewer that cannot be written to is dropped and no longer counted`() {
        val broken = object : OutputStream() {
            override fun write(byte: Int) = throw java.io.IOException("closed")
        }

        MapEvents(states).stream(broken)

        assertEquals(0, states.viewers)
    }

    @Test
    fun `viewers are counted while they watch`() {
        val output = Recording("\n\n")
        val viewer = thread { MapEvents(states).stream(output) }

        assertTrue(output.seen.await(30, TimeUnit.SECONDS))
        assertEquals(1, states.viewers)
        states.close()
        viewer.join()
        assertEquals(0, states.viewers)
    }

    @Test
    fun `waiting for a change ends when the states close and goes quiet when nothing changes`() {
        assertEquals(MapObservation.Quiet, states.await(1L, 5))

        states.close()

        assertEquals(MapObservation.Closed, states.await(1L, 60_000))
    }
}
