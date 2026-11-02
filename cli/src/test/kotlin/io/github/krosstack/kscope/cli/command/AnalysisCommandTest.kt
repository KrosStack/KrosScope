package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisCommandTest {
    @Test
    fun `analysis returns ranked files and query interpretation as json`() = inWorkspace { root ->
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        val result = runKscope("analyze", "Lib", "--workspace", root.toString(), "--format", "json")
        assertEquals(0, result.statusCode, result.output)
        val json = parseJson(result.stdout)
        assertTrue(json.getValue("files").jsonArray.isNotEmpty())
        assertTrue("query" in json)
    }
}
