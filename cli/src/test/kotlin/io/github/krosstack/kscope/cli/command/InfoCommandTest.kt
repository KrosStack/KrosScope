package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InfoCommandTest {
    @Test
    fun `info summarizes a scanned workspace as text`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        val text = runKscope("info", "--workspace", root.toString())
        assertEquals(0, text.statusCode)
        assertTrue(text.stdout.startsWith("Indexed 4 files in 1 module:"), text.stdout)
        assertTrue(text.stdout.contains("  Modules       1\n"), text.stdout)
        assertTrue(text.stdout.contains("Files by type (4)\n  gradle.build-script  1\n"), text.stdout)
        assertTrue(text.stdout.contains("  Search index  populated"), text.stdout)
    }

    @Test
    fun `info summarizes a scanned workspace as json`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        val info = parseJson(runKscope("info", "--workspace", root.toString(), "--format", "json").stdout)
        assertEquals(1, info.getValue("structure").jsonObject.getValue("modules").jsonPrimitive.int)
        assertEquals("true", info.getValue("indexed").jsonPrimitive.content)
        assertEquals("true", info.getValue("searchIndexPopulated").jsonPrimitive.content)
        assertEquals(0, info.getValue("metricsByName").jsonObject.size)
    }
}
