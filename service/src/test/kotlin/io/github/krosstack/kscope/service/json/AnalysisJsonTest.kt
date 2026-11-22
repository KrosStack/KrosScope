package io.github.krosstack.kscope.service.json

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.ChangeScope
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedModule
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.RiskFact
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.analysis.AnalysisJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalSerializationApi::class)
class AnalysisJsonTest {
    private val source = ArtifactRef(RepositoryId("repo"), RepoPath("source"))

    private fun symbol(index: Int) = RankedSymbol(
        Symbol(
            SymbolId("s$index"), SymbolKind("kotlin.function", SymbolCategory.CALLABLE), "s$index", null, null, null,
            null, emptySet(), SymbolOrigin.Source, null, null, null, Scope(), EvidenceClass.COMPILER, Certainty.EXACT,
        ),
        1.0 / (index + 1),
    )

    private fun analysis(files: Int, symbols: Int) = AnalysisResult(
        AnalyzedQuery("change", listOf("change"), emptyList(), emptyList(), emptyList()),
        (1..files).map { RankedFile(source.copy(path = RepoPath("f$it")), 1.0 / it, FileRole.READ, emptyList()) },
        (1..symbols).map(::symbol), emptyList(), emptyList(), emptyList(),
        ChangeScope(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), false),
        emptyList(), emptyList(), emptyMap(), "limited",
    )

    private fun populated(): AnalysisResult {
        val link = Relationship(
            from = NodeRef.OfArtifact(source),
            kind = RelationKind.REFERENCES,
            subKind = null,
            resolution = Resolution.Resolved(NodeRef.OfSymbol(SymbolId("s1")), Certainty.EXACT),
            evidenceClass = EvidenceClass.COMPILER,
            site = null,
            scope = Scope(),
        )
        return analysis(files = 3, symbols = 4).copy(
            modules = listOf(RankedModule(ModuleId("repo//:lib"), "lib", 0.5)),
            tests = listOf(RankedFile(source.copy(path = RepoPath("t1")), 2.0, FileRole.READ, emptyList())),
            boundaries = listOf(link),
            risks = listOf(RiskFact(NodeRef.OfArtifact(source), "fan-in", 3.0)),
            unresolved = listOf(link),
            evidence = mapOf("compiler" to 2),
            boundaryPaths = listOf(listOf(link, link)),
            boundaryPathsTruncated = true,
            contextEstimates = mapOf(ContextLevel.MINIMAL to TokenCount(10), ContextLevel.FULL to TokenCount(99)),
            semantic = SemanticStatus(SemanticState.BEHIND, provider = "recording", behindChunks = 4),
            stage = "lexical",
        )
    }

    private fun names(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet()

    private fun encoded(view: AnalysisJson): JsonObject = Json.parseToJsonElement(ServiceJson.encode(view)).jsonObject

    @Test
    fun `the view has the fields of the analysis and the two counts of what it leaves out`() {
        assertEquals(
            names(AnalysisResult.serializer().descriptor) + setOf("filesOmitted", "symbolsOmitted"),
            names(AnalysisJson.serializer().descriptor),
        )
    }

    @Test
    fun `the lists are the given ones and the counts say how many were left out`() {
        val result = analysis(files = 5, symbols = 8)

        val view = AnalysisJson.of(result, result.files.take(2), result.symbols.take(3))

        assertEquals(result.files.take(2), view.files)
        assertEquals(3, view.filesOmitted)
        assertEquals(result.symbols.take(3), view.symbols)
        assertEquals(5, view.symbolsOmitted)
        assertEquals(result.query, view.query)
        assertEquals(result.confidence, view.confidence)
    }

    @Test
    fun `a count is printed only above zero`() {
        val result = analysis(files = 3, symbols = 4)

        val whole = encoded(AnalysisJson.of(result, result.files, result.symbols))
        val cut = encoded(AnalysisJson.of(result, result.files.take(1), result.symbols.take(1)))

        assertFalse("filesOmitted" in whole.keys || "symbolsOmitted" in whole.keys, whole.keys.toString())
        assertEquals("2", cut.getValue("filesOmitted").jsonPrimitive.content)
        assertEquals("3", cut.getValue("symbolsOmitted").jsonPrimitive.content)
    }

    @Test
    fun `the analysis and its view are both schema version 3`() {
        val result = analysis(files = 1, symbols = 1)
        val full = Json.parseToJsonElement(ServiceJson.encode(result)).jsonObject
        val view = encoded(AnalysisJson.of(result, result.files, result.symbols))

        assertEquals("3", full.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals(full, view)
    }

    @Test
    fun `a view that omits nothing encodes as the populated analysis does`() {
        val result = populated()
        val full = Json.parseToJsonElement(ServiceJson.encode(result)).jsonObject
        val view = encoded(AnalysisJson.of(result, result.files, result.symbols))

        assertEquals(full, view)
        assertEquals("true", full.getValue("boundaryPathsTruncated").jsonPrimitive.content)
        assertEquals("lexical", full.getValue("stage").jsonPrimitive.content)
    }

    @Test
    fun `a cut view differs from the populated analysis in the two lists and the counts only`() {
        val result = populated()
        val full = Json.parseToJsonElement(ServiceJson.encode(result)).jsonObject
        val view = encoded(AnalysisJson.of(result, result.files.take(1), result.symbols.take(2)))

        val changed = setOf("files", "symbols", "filesOmitted", "symbolsOmitted")
        assertEquals(full.filterKeys { it !in changed }, view.filterKeys { it !in changed })
        assertEquals("2", view.getValue("filesOmitted").jsonPrimitive.content)
        assertEquals("2", view.getValue("symbolsOmitted").jsonPrimitive.content)
    }
}
