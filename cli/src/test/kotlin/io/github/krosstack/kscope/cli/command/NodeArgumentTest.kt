package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.writeFile
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeArgumentTest {
    @Test
    fun `node lookup accepts a source location and reports exact name ambiguity`() = inWorkspace { root ->
        writeFile(root, "lib/src/main/kotlin/Another.kt", "package other\nclass Lib\n")
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        val atLine = runKscope(
            "symbol",
            "lib/src/main/kotlin/Lib.kt:1",
            "--workspace",
            root.toString(),
            "--format",
            "json",
        )
        val ambiguous = runKscope("impact", "Lib", "--workspace", root.toString(), "--format", "json")

        assertEquals(0, atLine.statusCode, atLine.output)
        assertTrue(parseJson(atLine.stdout).getValue("symbols").jsonArray.isNotEmpty())
        assertEquals(2, ambiguous.statusCode, ambiguous.output)
        assertEquals(2, parseJson(ambiguous.stdout).getValue("candidates").jsonArray.size)
        assertEquals("", ambiguous.stderr)
    }
}
