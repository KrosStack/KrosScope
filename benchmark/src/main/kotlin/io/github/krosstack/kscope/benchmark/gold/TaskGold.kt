package io.github.krosstack.kscope.benchmark.gold

import kotlinx.serialization.Serializable

@Serializable
public data class GoldChangeScope(
    public val modules: List<String> = emptyList(),
    public val sourceSets: List<String> = emptyList(),
    public val variants: List<String> = emptyList(),
)

/** A development task with the files, symbols and relationships a correct change involves. */
@Serializable
public data class GoldTask(
    public val id: String,
    public val title: String,
    public val statement: String,
    public val category: String,
    public val mustEdit: List<String> = emptyList(),
    public val mustCreate: List<String> = emptyList(),
    public val mayEdit: List<String> = emptyList(),
    public val mustRead: List<String> = emptyList(),
    public val symbols: List<GoldAddress> = emptyList(),
    public val relationships: List<String> = emptyList(),
    public val changeScope: GoldChangeScope = GoldChangeScope(),
    public val relevantTests: List<GoldAddress> = emptyList(),
    public val notes: String? = null,
)
