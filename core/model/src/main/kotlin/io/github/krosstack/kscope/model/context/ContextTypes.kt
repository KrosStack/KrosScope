package io.github.krosstack.kscope.model.context

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
public value class TokenCount(public val value: Int) {
    init {
        require(value >= 0) { "Token count must be nonnegative." }
    }
}

@Serializable
@JvmInline
public value class TokenizerId(public val value: String)

@Serializable
public enum class ContextLevel {
    @SerialName("minimal")
    MINIMAL,

    @SerialName("recommended")
    RECOMMENDED,

    @SerialName("deep")
    DEEP,

    @SerialName("full")
    FULL,
}

@Serializable
public enum class ContextRole {
    @SerialName("edit")
    EDIT,

    @SerialName("read")
    READ,

    @SerialName("test")
    TEST,

    @SerialName("boundary")
    BOUNDARY,
}

@Serializable
public enum class ContextRendering {
    SIGNATURE,
    DECLARATION,
    OUTLINE,
    FILE,
    ENTRY,
    GENERATED,
}

@Serializable
public enum class ContextOmissionReason {
    BUDGET,
    UNREADABLE,
    BINARY,
    NO_SOURCE,
    INVALID_RANGE,
    LEVEL_LIMIT,
    GENERATED_BODY,
    STALE_INDEX,
    OVERSIZED,
}
