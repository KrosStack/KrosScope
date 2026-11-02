package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.Golden
import io.github.krosstack.kscope.cli.inGreeterWorkspace
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TextGoldenTest {
    private fun printed(root: Path, arguments: List<String>, expectedStatus: Int = 0): String {
        val result = runKscopeWith(arguments + listOf("--workspace", root.toString()))
        assertEquals(expectedStatus, result.statusCode, result.output)
        return result.stdout.replace(KrosScope.toolVersion(), "<version>")
    }

    @Test
    fun `commands print their text on a scanned workspace`() = inGreeterWorkspace { root ->
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        val file = "lib/src/main/kotlin/Greeter.kt"
        writeFile(root, "tasks/case.json", """{"id":"case","statement":"Greeter","mustEdit":["$file"]}""")

        mapOf(
            "info" to listOf("info"),
            "symbol" to listOf("symbol", "Greeter"),
            "analyze" to listOf("analyze", "greet a person by name"),
            "impact" to listOf("impact", file),
            "related" to listOf("related", file),
            "graph-modules" to listOf("graph"),
            "graph-neighborhood" to listOf("graph", "--scope", "neighborhood", "--node", file),
            "context" to listOf("context", "greet a person by name", "--budget", "2000"),
        ).mapValues { (_, arguments) -> printed(root, arguments) }.let { outputs ->
            val tasks = root.resolve("tasks").toString()
            val benchmark = runKscope("benchmark", "--tasks", tasks, "--workspace", root.toString())
            Golden.assertAllMatch(outputs + ("benchmark" to benchmark.stdout))
        }
    }

    @Test
    fun `an ambiguous name lists the candidates with what to pass`() = inGreeterWorkspace { root ->
        writeFile(root, "app/src/main/kotlin/Other.kt", "package other\n\nclass Greeter\n")
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)

        val repository = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
        val text = printed(root, listOf("impact", "Greeter"), expectedStatus = 2).replace(repository, "<repository>")
        Golden.assertMatches("ambiguous", text)
    }
}
