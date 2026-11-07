package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.contextInput
import io.github.krosstack.kscope.service.readWorkspaceFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MlOptionTest {
    @Test
    fun `ml defaults on and either off spelling reaches retrieval`() = inScannedWorkspace { root ->
        val base = listOf("analyze", "change Lib", "--workspace", root.toString(), "--format", "json")
        val cases = listOf(
            emptyList<String>() to "not-installed",
            listOf("--ml", "on") to "not-installed",
            listOf("--no-ml") to "off",
            listOf("--ml", "off") to "off",
            listOf("--no-ml", "--ml", "on") to "off",
            listOf("--ml", "on", "--no-ml") to "off",
        )
        for ((flags, state) in cases) {
            val result = runKscopeWith(base + flags)
            assertEquals(0, result.statusCode, result.output)
            assertEquals(
                state,
                parseJson(result.stdout).getValue("semantic").jsonObject["state"]?.jsonPrimitive?.content,
            )
        }
    }

    @Test
    fun `root options reach analysis and no ml wins across command boundaries`() = inScannedWorkspace { root ->
        val base = listOf("analyze", "change Lib", "--workspace", root.toString(), "--format", "json")
        val commands = listOf(
            listOf("--no-ml") + base,
            listOf("--ml", "off") + base,
            listOf("--no-ml") + base + listOf("--ml", "on"),
            listOf("--ml", "on") + base + "--no-ml",
        )
        for (command in commands) {
            val result = runKscopeWith(command)
            assertEquals(0, result.statusCode, result.output)
            assertEquals(
                "off",
                parseJson(result.stdout).getValue("semantic").jsonObject["state"]?.jsonPrimitive?.content,
            )
        }
    }

    @Test
    fun `rankings and context estimates are identical without a provider`() = inScannedWorkspace { root ->
        val base = listOf("analyze", "change Lib", "--workspace", root.toString(), "--format", "json")
        val on = parseJson(runKscopeWith(base).stdout)
        val off = parseJson(runKscopeWith(base + "--no-ml").stdout)

        assertTrue(on.getValue("files").toString().contains("Lib.kt"))
        assertEquals(on - "semantic", off - "semantic")
    }

    @Test
    fun `context packages from both cli modes are identical without a provider`() = inScannedWorkspace { root ->
        val base = listOf("analyze", "change Lib", "--workspace", root.toString(), "--format", "json")
        val analyses = listOf(emptyList(), listOf("--no-ml")).map { flags ->
            val json = parseJson(runKscopeWith(base + flags).stdout)
            Json.decodeFromJsonElement<AnalysisResult>(JsonObject(json - "schemaVersion" - "indexLevel"))
        }
        KrosScope.open(root).use { scope ->
            val compiler = ContextCompiler(
                scope.graph(),
                scope.readWorkspaceFiles(),
                TokenEstimators.create(TokenizerId("chars")),
            )
            val packages = analyses.map {
                compiler.compile(it.contextInput(), ContextLevel.RECOMMENDED, TokenCount(1000))
            }

            assertTrue(packages.first().markdown.contains("class Lib"))
            assertEquals(packages.first(), packages.last())
        }
    }

    @Test
    fun `text and markdown stay identical for tasks and name fallback`() = inScannedWorkspace { root ->
        for (format in listOf("text", "markdown")) {
            for (query in listOf("change the library", "MissingName")) {
                val base = listOf("analyze", query, "--workspace", root.toString(), "--format", format)
                val on = runKscopeWith(base)
                val off = runKscopeWith(base + "--no-ml")

                assertEquals(0, off.statusCode, off.output)
                assertEquals(on.stdout, off.stdout)
            }
        }
    }
}
