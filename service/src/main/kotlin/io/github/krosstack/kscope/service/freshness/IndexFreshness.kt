package io.github.krosstack.kscope.service.freshness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class FreshnessState {
    @SerialName("behind")
    BEHIND,

    @SerialName("not-behind")
    NOT_BEHIND,

    @SerialName("not-checked")
    NOT_CHECKED,
}

@Serializable
public data class FreshnessCounts(public val behind: Int, public val notBehind: Int, public val notChecked: Int)

@Serializable
public data class IndexFreshness(
    public val state: FreshnessState,
    public val scannedAt: String?,
    public val commits: FreshnessCounts,
    public val workingTrees: FreshnessCounts,
    public val changedFiles: Int,
    public val estimate: Boolean,
    public val reason: String?,
)
