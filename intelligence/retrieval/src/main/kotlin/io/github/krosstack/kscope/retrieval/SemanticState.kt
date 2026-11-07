package io.github.krosstack.kscope.retrieval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class SemanticState {
    @SerialName("off")
    OFF,

    @SerialName("not-installed")
    NOT_INSTALLED,

    @SerialName("disabled")
    DISABLED,

    @SerialName("used")
    USED,

    @SerialName("behind")
    BEHIND,

    @SerialName("worker-unavailable")
    WORKER_UNAVAILABLE,

    @SerialName("static-fallback")
    STATIC_FALLBACK,
}
