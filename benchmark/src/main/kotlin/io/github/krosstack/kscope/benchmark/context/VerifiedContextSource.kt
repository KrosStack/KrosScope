package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal data class SourceVerification(val spans: List<SourceSpan>, val issues: List<ContextSourceIssue>)

internal class VerifiedContextSource(
    private val documents: Map<ArtifactRef, SourceDocument>,
    private val estimator: TokenEstimator,
) {
    fun verify(context: ContextPackage): SourceVerification {
        val checked = context.fragments.map { check(it, context.markdown) }
        val spans = checked.filterIsInstance<CheckedSpan.Covered>().map { it.span }
        val issues = checked.mapIndexedNotNull { index, result ->
            (result as? CheckedSpan.Rejected)?.let { ContextSourceIssue(index, it.reason) }
        }
        return SourceVerification(spans, issues + packageIssues(context))
    }

    private fun check(fragment: ContextFragment, markdown: String): CheckedSpan = when {
        !fragment.sourceBacked -> CheckedSpan.Ignored
        fragment.rendering !in SOURCE_RENDERINGS -> CheckedSpan.Rejected(ContextSourceIssueReason.UNSUPPORTED_RENDERING)
        else -> source(fragment, markdown)
    }

    private fun source(fragment: ContextFragment, markdown: String): CheckedSpan {
        val document = documents[fragment.artifact]
        val range = fragment.range
        val interval = range?.let { document?.interval(it) }
        return when {
            document == null -> CheckedSpan.Rejected(ContextSourceIssueReason.UNKNOWN_ARTIFACT)

            range == null -> CheckedSpan.Rejected(ContextSourceIssueReason.NO_RANGE)

            interval == null -> CheckedSpan.Rejected(ContextSourceIssueReason.INVALID_RANGE)

            document.text.substring(interval.start, interval.end) != fragment.text ->
                CheckedSpan.Rejected(ContextSourceIssueReason.TEXT_MISMATCH)

            !markdown.contains(fragment.text) -> CheckedSpan.Rejected(ContextSourceIssueReason.TEXT_NOT_RENDERED)

            estimator.count(fragment.text) != fragment.tokens ->
                CheckedSpan.Rejected(ContextSourceIssueReason.TOKEN_MISMATCH)

            else -> CheckedSpan.Covered(SourceSpan(fragment.artifact, interval))
        }
    }

    private fun packageIssues(context: ContextPackage): List<ContextSourceIssue> = buildList {
        val actual = estimator.count(context.markdown)
        if (actual != context.totalTokens) add(ContextSourceIssue(null, ContextSourceIssueReason.TOKEN_MISMATCH))
        if (context.estimator != estimator.id) {
            add(ContextSourceIssue(null, ContextSourceIssueReason.ESTIMATOR_MISMATCH))
        }
        if (context.budget?.let { actual.value > it.value } == true) {
            add(ContextSourceIssue(null, ContextSourceIssueReason.OVER_BUDGET))
        }
    }

    private companion object {
        val SOURCE_RENDERINGS = setOf(ContextRendering.FILE, ContextRendering.DECLARATION, ContextRendering.ENTRY)
    }
}

private sealed interface CheckedSpan {
    data class Covered(val span: SourceSpan) : CheckedSpan
    data class Rejected(val reason: ContextSourceIssueReason) : CheckedSpan
    data object Ignored : CheckedSpan
}
