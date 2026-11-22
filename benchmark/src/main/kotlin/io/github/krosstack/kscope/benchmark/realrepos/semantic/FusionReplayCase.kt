package io.github.krosstack.kscope.benchmark.realrepos.semantic

import kotlinx.serialization.Serializable

@Serializable
internal data class FusionReplayCase(
    val id: String,
    val repository: String,
    val parent: String,
    val child: String,
    val queryKind: String,
    val structural: List<String>,
    val semantic: List<String>,
    val expectedFusedTop10: List<String>,
    val formatVersion: Int = 1,
)
