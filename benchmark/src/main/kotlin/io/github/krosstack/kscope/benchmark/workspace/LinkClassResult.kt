package io.github.krosstack.kscope.benchmark.workspace

import kotlinx.serialization.Serializable

/** What a scan was scored on: the sources alone, or the sources with the files that a build leaves on disk. */
internal enum class ScanPhase(val label: String) {
    SOURCES("sources"),
    BUILD_OUTPUTS("build outputs"),
}

/**
 * The agreement for one relationship class. [found] counts the gold relationships that a produced link joins,
 * [withExpectedEvidence] those of them whose link carries the expected evidence class, and [unmatched] the links of
 * the class between repositories that no gold relationship names. [missing] and [evidenceMismatches] say which.
 */
@Serializable
internal data class LinkClassResult(
    val name: String,
    val scan: String,
    val goldCount: Int,
    val found: Int,
    val recall: Double,
    val withExpectedEvidence: Int,
    val unmatched: Int,
    val missing: List<String>,
    val evidenceMismatches: List<String>,
) {
    val met: Boolean get() = goldCount > 0 && recall == 1.0 && evidenceMismatches.isEmpty()
}

@Serializable
internal data class WorkspaceConformanceResult(val repositories: List<String>, val classes: List<LinkClassResult>) {
    val passed: Boolean get() = classes.isNotEmpty() && classes.all { it.met }
}
