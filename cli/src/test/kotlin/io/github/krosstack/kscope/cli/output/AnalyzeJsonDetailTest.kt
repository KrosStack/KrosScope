package io.github.krosstack.kscope.cli.output

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.cli.writeFile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyzeJsonDetailTest {
    private fun inManySymbols(block: (Path) -> Unit) = inWorkspace { root ->
        val classes = (1..CLASSES).joinToString("\n") { "class Widget$it { fun render$it() = Unit }" }
        writeFile(root, "lib/src/main/kotlin/Widgets.kt", "$classes\n")
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        block(root)
    }

    private fun analyze(root: Path, options: List<String> = emptyList()) = runKscopeWith(
        listOf("analyze", "widget render", "--workspace", root.toString(), "--format", "json") + options,
    )

    private fun JsonObject.size(key: String): Int = getValue(key).jsonArray.size

    private fun JsonObject.omitted(key: String): Int = this[key]?.jsonPrimitive?.int ?: 0

    @Test
    fun `symbols are capped at each detail and the rest is counted`() = inManySymbols { root ->
        val full = parseJson(analyze(root, listOf("--detail", "full")).stdout)
        val standard = parseJson(analyze(root).stdout)
        val brief = parseJson(analyze(root, listOf("--detail", "brief")).stdout)
        val ranked = full.size("symbols") + full.omitted("symbolsOmitted")

        assertTrue(full.size("symbols") > 50, "the fixture must rank more than 50 symbols: ${full.size("symbols")}")
        assertEquals(50, standard.size("symbols"))
        assertEquals(10, brief.size("symbols"))
        assertEquals(ranked - 50, standard.omitted("symbolsOmitted"))
        assertEquals(ranked - 10, brief.omitted("symbolsOmitted"))
        assertEquals(full.getValue("symbols").jsonArray.take(10), brief.getValue("symbols").jsonArray.toList())
        assertEquals("3", standard.getValue("schemaVersion").jsonPrimitive.content)
    }

    @Test
    fun `an omitted count is absent when nothing was left out`() = inWorkspace { root ->
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)

        val result = parseJson(analyze(root).stdout)

        assertFalse("symbolsOmitted" in result.keys, result.keys.toString())
        assertFalse("filesOmitted" in result.keys, result.keys.toString())
    }

    @Test
    fun `detail is accepted with json for analyze and sections are not`() = inWorkspace { root ->
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)

        val detail = analyze(root, listOf("--detail", "brief"))
        val sections = analyze(root, listOf("--sections", "tests"))

        assertEquals(0, detail.statusCode, detail.output)
        assertEquals(2, sections.statusCode, sections.output)
        assertTrue(sections.stderr.contains("--sections applies to text and markdown output"), sections.stderr)
    }

    @Test
    fun `detail is still refused with json by the other commands`() = inWorkspace { root ->
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)

        listOf("symbol Lib", "related Lib", "impact Lib.kt", "graph", "info").forEach {
            val result = runKscopeWith(
                it.split(" ") + listOf("--detail", "brief", "--format", "json", "--workspace", root.toString()),
            )

            assertEquals(2, result.statusCode, "$it: ${result.output}")
        }
    }

    @Test
    fun `a server answers analyze json as the command line does`() = inManySymbols { root ->
        withTestServer(root) { server ->
            val details = listOf(emptyList(), listOf("--detail", "brief"), listOf("--detail", "full"))
            for (detail in details) {
                val local = analyze(root, listOf("--no-server") + detail)
                val served = analyze(root, detail)

                assertEquals(0, served.statusCode, served.output)
                assertEquals(local.stdout, served.stdout, detail.toString())
            }
            assertEquals(details.size, server.requestCount)
        }
    }

    private companion object {
        const val CLASSES = 120
    }
}
