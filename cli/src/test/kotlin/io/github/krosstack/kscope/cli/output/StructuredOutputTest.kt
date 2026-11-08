package io.github.krosstack.kscope.cli.output

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.runKscopeOnTerminal
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.leveled
import kotlin.test.Test
import kotlin.test.assertContentEquals

class StructuredOutputTest {
    @Test
    fun `json preserves line separators and its final newline`() = inScannedWorkspace { root ->
        val task = "Find Lib\u2028and explain its dependencies"
        val expected = KrosScope.open(root).use { scope ->
            val result = scope.leveled(scope.analyze(task))
            ServiceJson.encode(result.value, result.level) + "\n"
        }
        val actual = runKscopeOnTerminal(
            "analyze", task, "--workspace", root.toString(), "--format", "json", "--progress", "never",
            width = 12,
        )
        assertContentEquals(expected.toByteArray(Charsets.UTF_8), actual.stdout.toByteArray(Charsets.UTF_8))
    }
}
