package io.github.krosstack.kscope.cli.progress

import io.github.krosstack.kscope.service.ProgressStage
import io.github.krosstack.kscope.service.QueryStages
import io.github.krosstack.kscope.service.ScanStages

/** What a person reads for a stage and for the units it counts. */
internal object ProgressLabels {
    private val labels = mapOf(
        ScanStages.DISCOVERY to "Listing files",
        ScanStages.BUILD_MODEL to "Reading the build model",
        ScanStages.SOURCE_SETS to "Assigning source sets",
        ScanStages.SYNTHESIS to "Generating API surfaces",
        ScanStages.PRODUCTION to "Analysing sources",
        ScanStages.LINKING to "Linking references",
        ScanStages.CLEANUP to "Removing obsolete units",
        ScanStages.SEARCH_INDEX to "Indexing text for search",
        QueryStages.LOAD_INDEX to "Loading the index",
        QueryStages.RANK to "Ranking files and symbols",
        QueryStages.ESTIMATE to "Estimating context sizes",
        QueryStages.COMPILE to "Compiling context",
        QueryStages.SEARCH to "Searching symbols",
        QueryStages.IMPACT to "Tracing impact",
        QueryStages.RELATED to "Following relationships",
        QueryStages.GRAPH_VIEW to "Building the graph",
    )

    fun stage(stage: ProgressStage): String {
        val verified = stage.id.startsWith(ScanStages.VERIFY_PREFIX)
        val own = if (verified) ProgressStage(stage.id.removePrefix(ScanStages.VERIFY_PREFIX)) else stage
        val label = labels[own] ?: own.id
        return if (verified) "Verification: ${label.replaceFirstChar { it.lowercase() }}" else label
    }

    fun unit(unit: String, count: Long): String = if (count == 1L) unit.removeSuffix("s") else unit
}
