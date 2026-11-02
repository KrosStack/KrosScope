package io.github.krosstack.kscope.retrieval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class FileRole {
    @SerialName("edit")
    EDIT,

    @SerialName("read")
    READ,
}

@Serializable
public enum class SignalKind {
    @SerialName("lexical-symbol")
    LEXICAL_SYMBOL,

    @SerialName("lexical-artifact")
    LEXICAL_ARTIFACT,

    @SerialName("graph")
    GRAPH,

    @SerialName("category")
    CATEGORY,

    @SerialName("history")
    HISTORY,

    @SerialName("exact")
    EXACT,

    @SerialName("test-relation")
    TEST_RELATION,

    @SerialName("test-reference")
    TEST_REFERENCE,

    @SerialName("test-edit")
    TEST_EDIT,
}

@Serializable
public enum class RetrievalStage {
    LEXICAL,
    GRAPH,
    PROJECTION,
    CATEGORY,
    FULL,
}
