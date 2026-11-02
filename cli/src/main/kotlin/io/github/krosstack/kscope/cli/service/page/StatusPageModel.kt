package io.github.krosstack.kscope.cli.service.page

import io.github.krosstack.kscope.cli.serve.ScanRecord
import io.github.krosstack.kscope.cli.service.home.ServiceLimits

internal data class StatusPageModel(
    val version: String,
    val pid: Long,
    val startedAt: String,
    val limits: ServiceLimits,
    val heapUsedBytes: Long,
    val heapMaximumBytes: Long,
    val workspaces: List<WorkspaceStatus>,
)

internal data class WorkspaceStatus(
    val root: String,
    val state: String,
    val indexed: Boolean?,
    val indexLevel: String? = null,
    val inMemory: Boolean?,
    val lastScan: ScanRecord?,
    val watcher: String?,
    val settingsFile: String,
    val workers: String,
    val workerHeap: String,
    val errors: List<String>,
)
