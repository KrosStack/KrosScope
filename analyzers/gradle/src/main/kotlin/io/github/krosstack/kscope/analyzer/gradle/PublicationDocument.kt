package io.github.krosstack.kscope.analyzer.gradle

import kotlinx.serialization.Serializable

@Serializable
internal data class MavenPublicationDocument(
    val groupId: String? = null,
    val artifactId: String? = null,
    val version: String? = null,
)

@Serializable
internal data class PublicationDocument(
    val group: String? = null,
    val name: String? = null,
    val version: String? = null,
    val publications: List<MavenPublicationDocument> = emptyList(),
)
