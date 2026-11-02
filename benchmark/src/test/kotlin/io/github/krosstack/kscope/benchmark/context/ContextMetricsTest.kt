package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.localization.LocalizationSymbolAddress
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextMetricsTest {
    private val estimator = CountingEstimator()
    private val file = ArtifactRef(RepositoryId("test"), RepoPath("gold"))
    private val omitted = file.copy(path = RepoPath("omitted"))
    private val document = SourceDocument(file, "abcdefghi")
    private val documents = mapOf(file to document, omitted to SourceDocument(omitted, "unread"))
    private val short = LocalizationSymbolAddress("gold", name = "short")
    private val long = LocalizationSymbolAddress("gold", name = "long")
    private val gold = ResolvedContextGold(
        setOf(file, omitted),
        listOf(
            ResolvedGoldSymbol(short, listOf(SourceSpan(file, SourceInterval(0, 3)))),
            ResolvedGoldSymbol(long, listOf(SourceSpan(file, SourceInterval(0, 9)))),
        ),
    )

    @Test
    fun `overlapping real source spans count once and symbols require their entire declaration`() {
        val context = packageOf(listOf(fragment(0, 5), fragment(2, 8)), "meta\nabcdefgh\nforeign")
        val scores = ContextMetrics.score(context, gold, documents, estimator)

        assertEquals(0.5, scores.fileRecall)
        assertEquals(0.5, scores.symbolRecall)
        assertEquals(0.5, scores.goldContextRecall)
        assertEquals(0.0, scores.fullFileRecall)
        assertEquals(TokenCount(8), scores.goldSourceTokens)
        assertEquals(8.0 / 21.0, scores.tokenPrecision)
        assertEquals(listOf(short), scores.coveredSymbols)
        assertTrue(scores.sourceIssues.isEmpty())
    }

    @Test
    fun `adjacent coverage completes a symbol and full file while metadata stays in the denominator`() {
        val context = packageOf(listOf(fragment(0, 5), fragment(5, 9)), "meta\nabcdefghi")
        val scores = ContextMetrics.score(context, gold, documents, estimator)

        assertEquals(0.75, scores.goldContextRecall)
        assertEquals(1.0, scores.symbolRecall)
        assertEquals(0.5, scores.fullFileRecall)
        assertEquals(TokenCount(9), scores.goldSourceTokens)
        assertEquals(9.0 / 14.0, scores.tokenPrecision)
    }

    @Test
    fun `normalized signatures outlines and generated text cannot claim source body coverage`() {
        val fragments = listOf(ContextRendering.SIGNATURE, ContextRendering.OUTLINE, ContextRendering.GENERATED).map {
            fragment(0, 9).copy(rendering = it, sourceBacked = false)
        }
        val context = packageOf(fragments, "meta\nabcdefghi")
        val scores = ContextMetrics.score(context, gold, documents, estimator)

        assertEquals(0.0, scores.goldContextRecall)
        assertEquals(0.0, scores.tokenPrecision)
        assertEquals(TokenCount(0), scores.goldSourceTokens)
    }

    @Test
    fun `zero length source intervals do not count file presence or symbol coverage`() {
        val context = packageOf(listOf(fragment(0, 0)), "meta")
        val scores = ContextMetrics.score(context, gold, documents, estimator)

        assertEquals(0.0, scores.goldContextRecall)
        assertEquals(0.0, scores.fileRecall)
        assertEquals(0.0, scores.symbolRecall)
        assertTrue(scores.coveredFiles.isEmpty())
    }

    @Test
    fun `claimed source must match its actual range and appear in rendered content`() {
        val mismatch = packageOf(listOf(fragment(0, 5).copy(text = "false")), "meta\nfalse")
        val hidden = packageOf(listOf(fragment(0, 5)), "meta")

        assertEquals(
            ContextSourceIssueReason.TEXT_MISMATCH,
            ContextMetrics.score(mismatch, gold, documents, estimator).sourceIssues.single().reason,
        )
        assertEquals(
            ContextSourceIssueReason.TEXT_NOT_RENDERED,
            ContextMetrics.score(hidden, gold, documents, estimator).sourceIssues.single().reason,
        )
    }

    private fun fragment(start: Int, end: Int) = ContextFragment(
        file, TextRange(1, start + 1, 1, end + 1), NodeRef.OfArtifact(file), ContextRendering.DECLARATION,
        ContextRole.READ, 1.0, TokenCount(end - start), document.text.substring(start, end), true,
    )

    private fun packageOf(fragments: List<ContextFragment>, markdown: String) = ContextPackage(
        "task",
        ContextLevel.RECOMMENDED,
        TokenCount(100),
        estimator.id,
        estimator.count(markdown),
        fragments,
        markdown = markdown,
    )
}
