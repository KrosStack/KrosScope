package io.github.krosstack.kscope.benchmark.realrepos

import io.github.krosstack.kscope.benchmark.context.ContextSetResult
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.localization.LocalizationSetResult
import io.github.krosstack.kscope.service.ScanResult
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
internal enum class RealSplit { DEV, TEST }

@Serializable
internal data class RealRepositorySpec(val id: String, val checkout: String, val head: String)

@Serializable
internal data class RealWorkspaceMember(val repository: String, val parent: String, val commit: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class RealCommitTask(
    val id: String,
    val repository: String,
    val parent: String,
    val commit: String,
    val committedAt: Long,
    val statement: String,
    val goldFiles: List<String>,
    val split: RealSplit,
    val verify: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val level: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val named: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val workspace: List<RealWorkspaceMember> = emptyList(),
)

@Serializable
internal data class RealRepositoryManifest(
    val repositories: List<RealRepositorySpec>,
    val tasks: List<RealCommitTask>,
    val exclusions: Map<String, Map<String, Int>>,
    val policy: String,
    val schemaVersion: Int = 1,
)

@Serializable
internal enum class RealMeasurementStatus { COMPLETE, FAILED }

@Serializable
internal data class RealTimings(
    val fullScanMillis: Long? = null,
    val localizationMillis: Long? = null,
    val contextMillis: Long? = null,
    val incrementalMillis: Long? = null,
    val verificationMillis: Long? = null,
    val contextResumed: Boolean = false,
)

@Serializable
internal data class RealRepositoryMeasurement(
    val task: RealCommitTask,
    val identity: String,
    val status: RealMeasurementStatus,
    val timings: RealTimings,
    val corpus: LocalizationCorpusSummary? = null,
    val localization: LocalizationSetResult? = null,
    val context: ContextSetResult? = null,
    val scan: ScanResult? = null,
    val update: ScanResult? = null,
    val verification: ScanResult? = null,
    val failure: String? = null,
    val schemaVersion: Int = 1,
)
