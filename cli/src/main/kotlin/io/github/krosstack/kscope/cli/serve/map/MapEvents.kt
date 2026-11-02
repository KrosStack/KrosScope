package io.github.krosstack.kscope.cli.serve.map

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.OutputStream

/** Writes the state to one viewer when it changes, and a comment now and then so that a closed viewer is noticed. */
internal class MapEvents(private val states: MapStates, private val pingMillis: Long = PING_MILLIS) {
    /** Returns when the viewer is gone or the states are closed. */
    fun stream(output: OutputStream) {
        states.joined()
        try {
            var seen = 0L
            while (true) {
                when (val observed = states.await(seen, pingMillis)) {
                    is MapObservation.Changed -> {
                        write(output, "event: state\ndata: ${Json.encodeToString(observed.state)}\n\n")
                        seen = observed.sequence
                    }

                    MapObservation.Quiet -> write(output, ": ping\n\n")

                    MapObservation.Closed -> return
                }
            }
        } catch (_: IOException) {
            return
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            states.left()
        }
    }

    private fun write(output: OutputStream, text: String) {
        output.write(text.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    companion object {
        const val PING_MILLIS = 15_000L
    }
}
