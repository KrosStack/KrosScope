package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.status.IndexStatus
import io.github.krosstack.kscope.service.status.ServerStatus
import io.github.krosstack.kscope.service.status.StatusResult

internal class StatusReport(
    val workspace: String,
    val indexLocation: String,
    val index: IndexStatus,
    val server: ServerStatus,
) {
    val readable: Boolean get() = index is IndexStatus.Present

    fun result(): StatusResult = when (index) {
        is IndexStatus.Present -> StatusResult(
            workspace = workspace,
            indexLocation = indexLocation,
            indexed = true,
            indexLevel = index.level.wireName,
            scannedAt = index.scannedAt,
            ageSeconds = index.ageSeconds,
            freshness = index.freshness,
            repositories = index.repositories,
            server = server,
            checkMillis = index.checkMillis,
            toolVersion = KrosScope.toolVersion(),
        )

        else -> StatusResult(
            workspace = workspace,
            indexLocation = indexLocation,
            indexed = false,
            indexLevel = null,
            scannedAt = null,
            ageSeconds = null,
            freshness = null,
            repositories = 0,
            server = server,
            checkMillis = 0,
            toolVersion = KrosScope.toolVersion(),
        )
    }
}
