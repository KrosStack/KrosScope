package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.service.page.StatusPageModel

internal class ServiceStatusSnapshot(
    private val metadata: ServeMetadata,
    private val host: ServiceHost,
    private val heap: HeapStatistics,
) {
    fun take(): StatusPageModel = StatusPageModel(
        version = metadata.toolVersion,
        pid = metadata.pid,
        startedAt = metadata.startTime,
        limits = host.limits,
        heapUsedBytes = heap.reading().usedAfterCollection,
        heapMaximumBytes = heap.maximumBytes,
        workspaces = host.entries.map { WorkspaceStatusReader.read(it.path, it.state) },
    )
}
