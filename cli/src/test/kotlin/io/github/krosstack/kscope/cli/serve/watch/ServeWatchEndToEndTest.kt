package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.cli.writeFile
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeWatchEndToEndTest {
    @Test
    fun `the real watcher indexes an added function without a requested scan`() = inScannedWorkspace { root ->
        val settings = ServeSettings(
            watchEnabled = true,
            watch = WatchSettings(timing = WatchTiming(pollMillis = 50, quietMillis = 100, minimumGapMillis = 100)),
        )
        withTestServer(root, settings) { server ->
            writeFile(root, "lib/src/main/kotlin/Lib.kt", "class Lib\nfun watchedFunction() = 42\n")
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            var found = false
            while (!found && System.nanoTime() < deadline) {
                val result = server.query(listOf("symbol", "watchedFunction", "--format", "json"))
                assertEquals(0, result.exitCode)
                found = parseJson(result.stdout).getValue("symbols").jsonArray.any {
                    it.jsonObject.getValue("symbol").jsonObject.getValue("name").jsonPrimitive.content ==
                        "watchedFunction"
                }
                if (!found) Thread.sleep(50)
            }
            assertTrue(found)
        }
    }
}
