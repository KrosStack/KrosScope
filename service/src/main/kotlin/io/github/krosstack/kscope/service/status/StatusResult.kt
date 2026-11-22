package io.github.krosstack.kscope.service.status

import io.github.krosstack.kscope.service.freshness.IndexFreshness
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class ServerState {
    @SerialName("running")
    RUNNING,

    @SerialName("notRunning")
    NOT_RUNNING,

    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public data class ServerStatus(
    public val state: ServerState,
    public val pid: Long?,
    public val version: String?,
    public val startedAt: String?,
    public val stale: Boolean,
)

@Serializable
public data class StatusResult(
    public val workspace: String,
    public val indexLocation: String,
    public val indexed: Boolean,
    public val indexLevel: String?,
    public val scannedAt: String?,
    public val ageSeconds: Long?,
    public val freshness: IndexFreshness?,
    public val repositories: Int,
    public val server: ServerStatus,
    public val checkMillis: Long,
    public val toolVersion: String,
)
