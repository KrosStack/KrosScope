package io.github.krosstack.kscope.benchmark.realrepos.corpus

import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate

internal data class CorpusCommit(
    val hash: String,
    val parent: String?,
    val timestamp: Long,
    val authors: String,
    val message: String,
)

internal data class CorpusChange(val status: String, val before: String, val after: String)

internal data class CorpusEligibility(
    val files: List<String> = emptyList(),
    val exclusion: String? = null,
    val statementPaths: List<String> = files,
)

internal class CorpusEligibilityReader(private val git: CorpusGit) {
    private val detector = RealSourceDetector()

    fun inspect(commit: CorpusCommit): CorpusEligibility {
        val parent = commit.parent
        val early = when {
            parent == null || !git.contains(parent) -> CorpusExclusions.MISSING_PARENT
            BOT.containsMatchIn(commit.authors) -> CorpusExclusions.BOT
            FORMATTING.containsMatchIn(commit.message.lineSequence().first()) -> CorpusExclusions.FORMATTING
            else -> null
        }
        if (early != null) return CorpusEligibility(exclusion = early)
        val sources = changes(checkNotNull(parent), commit.hash).filter { source(it.before) || source(it.after) }
        val gold = sources.filter { source(it.before) && !it.status.startsWith('A') && !it.status.startsWith('C') }
        val exclusion = classify(commit, sources, gold)
        return CorpusEligibility(
            gold.map { it.before }.distinct().sorted(),
            exclusion,
            sources.flatMap { listOf(it.before, it.after) }.distinct().sorted(),
        )
    }

    private fun classify(commit: CorpusCommit, sources: List<CorpusChange>, gold: List<CorpusChange>): String? = when {
        gold.isEmpty() -> CorpusExclusions.NO_PARENT_SOURCE_FILES
        gold.any { it.status.startsWith('T') } -> CorpusExclusions.CHANGED_SOURCE_TYPE
        gold.map { it.before }.distinct().size > MAX_FILES -> CorpusExclusions.TOO_MANY_SOURCE_FILES
        sources.all { it.status == "R100" } -> CorpusExclusions.PURE_RENAME
        sources.all { unchanged(commit, it) } -> CorpusExclusions.WHITESPACE_ONLY
        else -> null
    }

    private fun unchanged(commit: CorpusCommit, change: CorpusChange): Boolean {
        if (change.status.startsWith(
                'A',
            ) || change.status.startsWith('C') || change.status.startsWith('D')
        ) {
            return false
        }
        val before = git.read("show", "${commit.parent}:${change.before}")
        val after = git.read("show", "${commit.hash}:${change.after}")
        return if (detector.supportsHorizontalFormatting(change.before)) {
            CorpusFormatting.normalForm(before) == CorpusFormatting.normalForm(after)
        } else {
            before == after
        }
    }

    private fun source(path: String): Boolean =
        detector.detect(ArtifactCandidate(RepoPath(path), 0) { byteArrayOf() }) != null

    private fun changes(parent: String, commit: String): List<CorpusChange> {
        val fields = git.read(
            "diff-tree", "--no-commit-id", "--no-ext-diff", "--no-textconv", "-r", "-M",
            "--name-status", "-z", parent, commit,
        ).split('\u0000').dropLast(1)
        val changes = mutableListOf<CorpusChange>()
        var position = 0
        while (position < fields.size) {
            val status = fields[position++]
            val before = fields[position++]
            val after = if (status.startsWith('R') || status.startsWith('C')) fields[position++] else before
            changes.add(CorpusChange(status, before, after))
        }
        return changes
    }

    private companion object {
        const val MAX_FILES = 8
        val BOT = Regex("(?i)\\[bot]|\\bbot\\b|dependabot|renovate")
        val FORMATTING = Regex(
            "(?i)^(?:style(?:\\([^)]*\\))?:|(?:reformat|ktlint|spotless)\\b|" +
                "(?:run|apply)\\s+(?:ktlint|spotless)\\b|format(?:ting)?\\s+(?:code|sources?|files?|imports)\\b)",
        )
    }
}
