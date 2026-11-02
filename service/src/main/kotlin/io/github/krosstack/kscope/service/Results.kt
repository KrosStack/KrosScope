package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.service.settings.SettingsReport
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
public enum class Severity {
    @SerialName("error")
    ERROR,

    @SerialName("warning")
    WARNING,

    @SerialName("info")
    INFO,
}

@Serializable
public data class DiagnosticEntry(public val severity: Severity, public val code: String, public val message: String)

@Serializable
public data class DiagnosticSummary(
    public val errors: Int,
    public val warnings: Int,
    public val infos: Int,
    public val entries: List<DiagnosticEntry>,
)

@Serializable
public data class DifferenceEntry(public val producer: String, public val unit: String, public val difference: String)

/** The outcome of comparing an incremental update with a clean rebuild, of the facts and of the search index. */
@Serializable
public data class VerificationResult(
    public val matches: Boolean,
    public val differingUnits: Int,
    public val differences: List<DifferenceEntry>,
    public val differingSearchDocuments: Int = 0,
)

/**
 * [indexLevel] is the level the index has after the scan. [stageMillis] is timing information and varies between runs;
 * everything else is deterministic.
 */
@Serializable
public data class ScanResult(
    public val artifacts: Int,
    public val unitsProduced: Int,
    public val unitsSkipped: Int,
    public val unitsRemoved: Int,
    public val diagnostics: DiagnosticSummary,
    public val verification: VerificationResult?,
    public val stageMillis: Map<String, Long>,
    public val indexLocation: String,
    public val indexLevel: String = ScanLevel.RESOLVED.wireName,
)

@Serializable
public data class StructureSummary(
    public val repositories: Int,
    public val buildRoots: Int,
    public val modules: Int,
    public val sourceSets: Int,
    public val targets: Int,
    public val variants: Int,
)

@Serializable
public data class CoverageEntry(
    public val relation: String,
    public val evidence: String,
    public val state: String,
    public val count: Long,
)

/**
 * One repository of a workspace of several. [directory] is where its checkout lies relative to the workspace root;
 * with it a program builds the workspace path of a file. [modules] is the number of its modules and is not part of
 * the JSON.
 */
@Serializable
public data class RepositoryEntry(
    public val id: String,
    public val name: String,
    public val directory: String,
    public val headRevision: String?,
    @Transient public val modules: Int = 0,
)

/**
 * [indexed] is false when the workspace has not been scanned yet; the counts are then empty. [metricsByName] counts the
 * numeric node metrics, such as `history.commits`; [searchIndexPopulated] says whether the search index has documents.
 * [settings] are the settings of the workspace and where each value comes from; they do not depend on the index.
 * [repositories] lists the repositories in directory order and is left out unless the workspace has several.
 * [indexLevel] is the level the index was built at, or null while there is no index.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class InfoResult(
    public val toolVersion: String,
    public val indexLocation: String,
    public val indexed: Boolean,
    public val structure: StructureSummary,
    public val artifactsByType: Map<String, Long>,
    public val symbolsByKind: Map<String, Long>,
    public val coverage: List<CoverageEntry>,
    public val diagnostics: Int,
    public val metricsByName: Map<String, Long> = emptyMap(),
    public val searchIndexPopulated: Boolean = false,
    public val settings: SettingsReport,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val repositories: List<RepositoryEntry> = emptyList(),
    public val indexLevel: String? = null,
)
