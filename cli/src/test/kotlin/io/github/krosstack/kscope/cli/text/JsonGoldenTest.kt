package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.Golden
import io.github.krosstack.kscope.cli.inGreeterWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.service.KrosScope
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonGoldenTest {
    private val repositoryId = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
    private val indexLocation = Regex("\"indexLocation\": \"[^\"]*\"")

    @Test
    fun `commands print their json on a scanned workspace of one repository`() = inGreeterWorkspace { root ->
        writeFile(root, ".kscope/repository-id", "fixed-repository-id")
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)

        mapOf(
            "json-info" to listOf("info"),
            "json-symbol" to listOf("symbol", "Greeter"),
            "json-analyze" to listOf("analyze", "greet a person by name"),
            "json-context" to listOf("context", "greet a person by name", "--budget", "2000"),
        ).mapValues { (_, arguments) ->
            val result = runKscopeWith(arguments + listOf("--workspace", root.toString(), "--format", "json"))
            assertEquals(0, result.statusCode, result.output)
            result.stdout.replace(KrosScope.toolVersion(), "<version>")
                .replace(repositoryId, "<repository>")
                .replace(indexLocation, "\"indexLocation\": \"<index>\"")
        }.let(Golden::assertAllMatch)
    }
}
