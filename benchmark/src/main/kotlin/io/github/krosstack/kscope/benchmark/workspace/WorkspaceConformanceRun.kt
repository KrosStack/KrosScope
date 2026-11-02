package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Files
import java.nio.file.Path

/**
 * Scores the links between the repositories of the workspace fixture. The fixture is copied below [scratchParent] into
 * Git repositories and scanned there, so that the fixture stays untouched. The classes whose gold needs the output of
 * a build are scored on a second scan, after the outputs of [goldDirectory] were written; the others on the first.
 */
internal class WorkspaceConformanceRun(
    private val fixture: Path,
    private val goldDirectory: Path,
    private val scratchParent: Path,
) {
    fun run(): WorkspaceConformanceResult {
        val gold = WorkspaceGoldLoader.load(goldDirectory).relationships.groupBy { it.relationshipClass }.toSortedMap()
        val outputs = WorkspaceOutputs.load(WorkspaceGoldLoader.outputsFile(goldDirectory))
        val scratch = Files.createTempDirectory(scratchParent, "kscope-workspace-")
        try {
            val root = Files.createDirectories(scratch.resolve("workspace"))
            val repositories = WorkspacePreparer().prepare(fixture, root)
            val options = measuredOpenOptions(scratch.resolve("index"))
            val classes = KrosScope.open(root, options).use { scope ->
                scope.scan()
                val sources = scoreAll(WorkspaceGraph(scope.graph()), gold, ScanPhase.SOURCES)
                WorkspaceOutputs.write(root, outputs)
                scope.scan()
                sources + scoreAll(WorkspaceGraph(scope.graph()), gold, ScanPhase.BUILD_OUTPUTS)
            }
            return WorkspaceConformanceResult(repositories, classes.sortedBy { it.name })
        } finally {
            scratch.toFile().deleteRecursively()
        }
    }

    private fun scoreAll(
        graph: WorkspaceGraph,
        gold: Map<String, List<GoldRelationship>>,
        phase: ScanPhase,
    ): List<LinkClassResult> {
        val scorer = WorkspaceLinkScorer(graph)
        return gold.filterValues { WorkspaceLinkScorer.phaseOf(it) == phase }
            .map { (name, entries) -> scorer.score(name, LinkClasses.all[name], entries) }
    }
}
