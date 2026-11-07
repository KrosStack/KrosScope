package io.github.krosstack.kscope.cli.command.stage

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.contextInput
import io.github.krosstack.kscope.service.readWorkspaceFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StageOptionTest {
    @Test
    fun `stage defaults to the full pipeline`() = inScannedWorkspace { root ->
        val base = listOf("analyze", "change Lib", "--workspace", root.toString(), "--format", "json")

        assertEquals("full", analysis(base).stage)
    }

    @Test
    fun `analyze inherits the root stage option`() = inScannedWorkspace { root ->
        val base = listOf("analyze", "change Lib", "--workspace", root.toString(), "--format", "json")

        assertEquals("lexical", analysis(listOf("--stage", "lexical") + base).stage)
    }

    @Test
    fun `the command stage option overrides the root stage option`() = inScannedWorkspace { root ->
        val base = listOf("analyze", "change Lib", "--workspace", root.toString(), "--format", "json")

        assertEquals("full", analysis(listOf("--stage", "lexical") + base + listOf("--stage", "full")).stage)
    }

    @Test
    fun `an unknown stage is refused before analysis`() {
        val result = runKscope("analyze", "change Lib", "--stage", "unknown")

        assertEquals(2, result.statusCode, result.output)
    }

    @Test
    fun `lexical option returns the lexical ranking on a structurally connected fixture`() = inStageWorkspace { root ->
        val base = listOf("analyze", "change Needle", "--workspace", root.toString(), "--format", "json")
        val rankings = listOf("lexical", "full").map { stage ->
            analysis(base + listOf("--stage", stage)).files.map { it.artifact.path.value.substringAfterLast('/') }
        }

        assertEquals(listOf(listOf("Needle.kt"), listOf("Needle.kt", "Base.kt", "Parent.kt")), rankings)
    }

    @Test
    fun `text and markdown use the lexical result for task reading`() = inStageWorkspace { root ->
        val outputs = listOf("text", "markdown").map { format ->
            runKscope(
                "analyze", "change Needle", "--workspace", root.toString(), "--format", format,
                "--stage", "lexical", "--sections", "headline",
            ).stdout
        }

        assertTrue(outputs.all { it.contains("1 file scored") }, outputs.joinToString())
    }

    @Test
    fun `a missed name uses lexical task ranking`() = inStageWorkspace { root ->
        val result = runKscope(
            "analyze",
            "NeedleMissing",
            "--workspace",
            root.toString(),
            "--stage",
            "lexical",
            "--sections",
            "headline",
        )

        assertTrue(result.stdout.contains("1 file scored"), result.output)
    }

    @Test
    fun `context package from lexical json matches the lexical service result`() = inStageWorkspace { root ->
        val base = listOf("analyze", "change Needle", "--workspace", root.toString(), "--format", "json")
        val result = analysis(base + listOf("--stage", "lexical"))
        KrosScope.open(root).use { scope ->
            val expected = scope.analyze("change Needle", RetrievalStage.LEXICAL)
            val compiler = ContextCompiler(
                scope.graph(),
                scope.readWorkspaceFiles(),
                TokenEstimators.create(TokenizerId("chars")),
            )
            val packages = listOf(expected, result).map {
                compiler.compile(it.contextInput(), ContextLevel.RECOMMENDED, TokenCount(1000))
            }

            assertEquals(packages.first(), packages.last())
        }
    }

    private fun analysis(arguments: List<String>): AnalysisResult {
        val json = parseJson(runKscopeWith(arguments).stdout)
        return Json.decodeFromJsonElement<AnalysisResult>(JsonObject(json - "schemaVersion" - "indexLevel"))
    }

    private fun inStageWorkspace(block: (Path) -> Unit) = inWorkspace { root ->
        writeFile(root, "lib/src/main/kotlin/Needle.kt", "class Needle : Base()\n")
        writeFile(root, "lib/src/main/kotlin/Base.kt", "open class Base : Parent()\n")
        writeFile(root, "lib/src/main/kotlin/Parent.kt", "open class Parent\n")
        check(runKscope("scan", "--workspace", root.toString()).statusCode == 0)
        block(root)
    }
}
