package io.github.krosstack.kscope.benchmark.realrepos.corpus

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositorySpec
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import java.nio.file.Path

internal object RealRepositoryCorpus {
    fun prepare(repositories: List<RealRepositorySpec>, limit: Int = DEFAULT_LIMIT): RealRepositoryManifest {
        require(limit in 1..DEFAULT_LIMIT) { "Per-repository task limit must be between 1 and 60" }
        require(repositories.map { it.id }.distinct().size == repositories.size) { "Repository IDs must be unique" }
        val ordered = repositories.sortedBy { it.id }
        val exclusions = sortedMapOf<String, Map<String, Int>>()
        val tasks = ordered.flatMap { repository ->
            val result = candidates(repository)
            val recent = result.first.sortedWith(compareBy<RealCommitTask> { it.committedAt }.thenBy { it.commit })
                .takeLast(limit)
            val skipped = CorpusExclusions.SELECTION_LIMIT to result.first.size - recent.size
            exclusions[repository.id] = (result.second + skipped).toSortedMap()
            CorpusSelection.split(recent)
        }
        val selected = CorpusSelection.verification(tasks)
        val policy = POLICY + " Per-repository limit=$limit. Verification=${selected.count { it.verify }}/5."
        return RealRepositoryManifest(ordered, selected, exclusions, policy)
    }

    private fun candidates(repository: RealRepositorySpec): Pair<List<RealCommitTask>, Map<String, Int>> {
        require(COMMIT_HASH.matches(repository.head)) { "Repository HEAD must be a full commit hash" }
        val git = CorpusGit(Path.of(repository.checkout))
        val eligibility = CorpusEligibilityReader(git)
        val exclusions = mutableMapOf<String, Int>()
        val tasks = history(git, repository.head).mapNotNull { commit ->
            val result = eligibility.inspect(commit)
            val statement = CorpusStatement.clean(commit.message, result.statementPaths)
            val reason = result.exclusion ?: if (statement.isBlank()) CorpusExclusions.EMPTY_STATEMENT else null
            if (reason != null) {
                exclusions[reason] = exclusions.getOrDefault(reason, 0) + 1
                null
            } else {
                RealCommitTask(
                    "${repository.id}:${commit.hash}",
                    repository.id,
                    checkNotNull(commit.parent),
                    commit.hash,
                    commit.timestamp,
                    statement,
                    result.files,
                    RealSplit.TEST,
                )
            }
        }
        return tasks to exclusions.toSortedMap()
    }

    private fun history(git: CorpusGit, head: String): List<CorpusCommit> {
        val fields = git.read(
            "log",
            "--no-merges",
            "--max-count=$HISTORY_LIMIT",
            "-z",
            "--format=%H%x00%P%x00%ct%x00%an <%ae> %cn <%ce>%x00%B",
            head,
            "--",
        ).split('\u0000').dropLast(1)
        check(fields.size % HISTORY_FIELDS == 0) { "Unexpected Git history record shape" }
        return fields.chunked(HISTORY_FIELDS).map { values ->
            CorpusCommit(
                values[HASH_FIELD],
                values[PARENT_FIELD].takeIf { it.isNotBlank() },
                values[TIME_FIELD].toLong(),
                values[AUTHORS_FIELD],
                values[MESSAGE_FIELD],
            )
        }
    }

    private const val DEFAULT_LIMIT = 60
    private const val HISTORY_LIMIT = 400
    private const val HISTORY_FIELDS = 5
    private const val HASH_FIELD = 0
    private const val PARENT_FIELD = 1
    private const val TIME_FIELD = 2
    private const val AUTHORS_FIELD = 3
    private const val MESSAGE_FIELD = 4
    private val COMMIT_HASH = Regex("(?:[a-fA-F0-9]{40}|[a-fA-F0-9]{64})")
    private const val POLICY = "Inspect the 400 most recent reachable non-merge commits at each pinned HEAD; " +
        "select recent eligible commits by committer timestamp and SHA. Require 1-8 existing changed source files " +
        "at the parent, including deletions and parent paths of renames. New/copied files are omitted from gold. " +
        "Reject unavailable parents, bots, source type changes, pure renames, " +
        "conservative horizontal-whitespace-only and explicitly code-formatter-labelled changes. " +
        "Ancillary non-source edits are allowed. " +
        "Gold comes from the parent Git tree and is never reduced by what the index contains; " +
        "gold files the index lacks count as misses. " +
        "Source suffixes: kt,kts,java,gradle,xml,aidl,proto,sq,sql,c,cc,cpp,cxx,h,hpp. " +
        "Statements remove changed source paths, basenames and PR numbers, preserving bare domain words. " +
        "Older floor(n/2) tasks are DEV; " +
        "the remainder are TEST per repository. Five verification slots are allocated round-robin by repository ID " +
        "and evenly spaced chronologically within each repository before scoring."
}
