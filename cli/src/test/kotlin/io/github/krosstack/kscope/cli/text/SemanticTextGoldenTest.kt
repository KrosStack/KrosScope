package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.Golden
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.serve.semantic.SemanticQueryFixture
import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Readings
import io.github.krosstack.kscope.cli.text.format.Section
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.context.ContextRequest
import io.github.krosstack.kscope.service.contextResult
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemanticTextGoldenTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `standard and full analysis preserve the complete fused ten`() {
        for (state in listOf(SemanticState.USED, SemanticState.BEHIND)) {
            val analysis = applied(state)
            assertEquals(analysis.files.map { it.artifact }, AnalyzeText.relevantFiles(analysis))
            for (detail in listOf(DetailLevel.STANDARD, DetailLevel.FULL)) {
                val text = AnalyzeText.render(analysis, ResultFixtures.captured(detail), ResultFixtures.kinds)
                assertEquals(analysis.files.map { it.artifact.path.value }, paths(text, analysis))
                assertTrue(text.contains("Ranked files"), text)
                assertTrue(text.contains(if (state == SemanticState.USED) "used" else "behind 7 chunks"), text)
                assertFalse(text.contains("scored below"), text)
                assertFalse(text.contains("Files to edit"), text)
                assertTrue(text.contains("document"), text)
                assertTrue(text.contains("binary"), text)
                assertSections(analysis, detail)
            }
        }
        val fixture = fixture()
        Golden.assertMatches("render-analyze-semantic-standard", printed(fixture, fixture.analyze))
    }

    @Test
    fun `brief presents the first five without altering JSON`() {
        val analysis = applied(SemanticState.USED)
        val text = AnalyzeText.render(analysis, ResultFixtures.captured(DetailLevel.BRIEF), ResultFixtures.kinds)
        assertEquals(analysis.files.take(5).map { it.artifact.path.value }, paths(text, analysis))
        assertTrue(text.contains("Ranked files"), text)
        assertTrue(text.contains("5 more relevant (use --detail full to list them)"), text)
        val fixture = fixture()
        val json = printed(fixture, fixture.analyze + listOf("--format", "json"))
        val files = parseJson(json).getValue("files").jsonArray
        assertEquals(10, files.size)
        for (detail in listOf("brief", "standard", "full")) {
            val rendered = printed(fixture, fixture.analyze + listOf("--detail", detail))
            val shown = files.map {
                it.jsonObject.getValue("artifact").jsonObject.getValue("path").jsonPrimitive.content
            }
            val expected = if (detail == "brief") shown.take(5) else shown
            assertEquals(expected, rendered.lines().flatMap { line -> shown.filter { line.contains(it) } })
        }
        assertEquals(json, printed(fixture, fixture.analyze + listOf("--format", "json")))
        val small = fixture(documentCount = 2)
        Golden.assertAllMatch(
            mapOf(
                "render-analyze-semantic-brief" to printed(fixture, fixture.analyze + listOf("--detail", "brief")),
                "json-analyze-semantic" to compact(printed(small, small.analyze + listOf("--format", "json"))),
            ),
        )
    }

    @Test
    fun `applied context text names the actual status while JSON remains context v1`() {
        val fixture = fixture(documentCount = 2)
        val text = printed(fixture, fixture.context)
        assertTrue(text.contains("Semantic ranking  used"), text)
        val json = printed(fixture, fixture.context + listOf("--format", "json"))
        val result = parseJson(json)
        assertEquals("1", result.getValue("schemaVersion").jsonPrimitive.content)
        assertFalse(result.containsKey("semantic"))
        val markdown = printed(fixture, fixture.context + listOf("--format", "markdown"))
        assertEquals(result.getValue("markdown").jsonPrimitive.content.trimEnd(), markdown.trimEnd())
        assertFalse(markdown.contains("Semantic ranking"), markdown)
        fixture.open(fixture.root, OpenOptions()).use { scope ->
            val answer = scope.contextResult(ContextRequest("anchor", budget = TokenCount(500)))
            assertContextStatuses(answer.context)
            assertContextStatuses(answer.context.copy(fragments = emptyList()))
        }
        Golden.assertAllMatch(
            mapOf("render-context-semantic" to text, "json-context-semantic" to compact(json)),
        )
        assertEquals(fixture.fake.workersStarted, fixture.fake.workersClosed)
    }

    @Test
    fun `all fallback goldens remain unchanged`() {
        for (state in SemanticState.entries - setOf(SemanticState.USED, SemanticState.BEHIND)) {
            val analysis = ResultFixtures.analysis().copy(semantic = SemanticStatus(state))
            for (detail in DetailLevel.entries) {
                val text = AnalyzeText.render(
                    analysis,
                    ResultFixtures.captured(detail),
                    ResultFixtures.kinds,
                    Readings.TASK,
                )
                Golden.assertMatches("render-analyze-${detail.name.lowercase()}", text)
            }
            val sections = ResultFixtures.captured(sections = setOf(Section.TESTS, Section.DOCUMENTS))
            val text = AnalyzeText.render(analysis, sections, ResultFixtures.kinds, Readings.TASK)
            Golden.assertMatches("render-analyze-sections", text)
        }
    }

    private fun fixture(documentCount: Int = 10): SemanticQueryFixture =
        SemanticQueryFixture(directory.resolve("fixture-$documentCount"), documentCount, documentLines = 1)

    private fun printed(fixture: SemanticQueryFixture, arguments: List<String>): String {
        val result = fixture.local(arguments)
        assertEquals(0, result.statusCode, result.output)
        return result.stdout
    }

    private fun compact(text: String): String = parseJson(text).toString()
        .replace(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"), "<repository>")

    private fun applied(state: SemanticState): AnalysisResult {
        val files = listOf(ResultFixtures.file("src/Anchor.kt", 0.03, FileRole.EDIT)) +
            (1..7).map { ResultFixtures.file("src/Support$it.kt", 0.001 / it) } + listOf(
                ResultFixtures.file(ResultFixtures.DOCUMENT, 0.00001),
                ResultFixtures.file(ResultFixtures.IMAGE, 0.000001),
            )
        return ResultFixtures.analysis(files).copy(semantic = SemanticStatus(state, behindChunks = 7))
    }

    private fun paths(text: String, analysis: AnalysisResult): List<String> = text.lines().flatMap { line ->
        analysis.files.map { it.artifact.path.value }.filter { line.contains(it) }
    }

    private fun assertSections(analysis: AnalysisResult, detail: DetailLevel) {
        val expected = mapOf(
            Section.EDIT to listOf("src/Anchor.kt"),
            Section.READ to (1..7).map { "src/Support$it.kt" },
            Section.DOCUMENTS to listOf(ResultFixtures.DOCUMENT),
            Section.TOP to analysis.files.map { it.artifact.path.value },
        )
        for ((section, files) in expected) {
            val context = ResultFixtures.captured(detail, setOf(section))
            val text = AnalyzeText.render(analysis, context, ResultFixtures.kinds)
            assertEquals(files, paths(text, analysis), text)
            assertFalse(text.contains("Task"), text)
        }
    }

    private fun assertContextStatuses(context: ContextPackage) {
        val captured = ResultFixtures.captured()
        val original = ContextText.render(context, captured)
        for (state in SemanticState.entries) {
            val text = ContextText.render(context, captured, SemanticStatus(state, behindChunks = 7))
            when (state) {
                SemanticState.USED -> assertTrue(text.contains("Semantic ranking  used"), text)
                SemanticState.BEHIND -> assertTrue(text.contains("Semantic ranking  behind 7 chunks"), text)
                else -> assertEquals(original, text)
            }
        }
    }
}
