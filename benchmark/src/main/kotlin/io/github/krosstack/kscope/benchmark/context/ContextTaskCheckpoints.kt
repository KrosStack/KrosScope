package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.model.context.TokenCount
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.HexFormat

public interface ContextTaskCheckpoints {
    public fun load(request: ContextCheckpointRequest): Map<TokenCount, TaskContextResult>?
    public fun save(request: ContextCheckpointRequest, results: Map<TokenCount, TaskContextResult>)
}

@Serializable
public data class ContextCheckpointRequest(
    public val set: BenchmarkSet,
    public val taskId: String,
    public val fingerprint: String,
)

internal fun contextFingerprint(text: String): String = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)),
)
