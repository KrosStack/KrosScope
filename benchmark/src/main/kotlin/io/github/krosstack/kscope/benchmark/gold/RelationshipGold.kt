package io.github.krosstack.kscope.benchmark.gold

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The planted boundary relationships of the reference workspace, complete per class. */
@Serializable
public data class GoldRelationships(
    public val meta: GoldRelationshipMeta,
    public val relationships: List<GoldRelationship>,
)

@Serializable
public data class GoldRelationshipMeta(
    public val workspaceRoot: String,
    public val evidenceClasses: List<String> = emptyList(),
    public val classDefinitions: Map<String, String> = emptyMap(),
    public val counts: Map<String, Int> = emptyMap(),
)

/**
 * One relationship of a class. [kind], [line], [via], [role] and [scope] qualify it in ways that depend on the class;
 * a field a class does not use stays null. [requiresOutput] says that only a build output, not the sources, proves it.
 */
@Serializable
public data class GoldRelationship(
    public val id: String,
    @SerialName("class") public val relationshipClass: String,
    public val from: GoldAddress,
    public val to: GoldAddress,
    public val expectedEvidence: String,
    public val kind: String? = null,
    public val line: Int? = null,
    public val via: String? = null,
    public val role: String? = null,
    public val scope: String? = null,
    public val conventionPlugins: List<String> = emptyList(),
    public val requiresOutput: Boolean = false,
)
