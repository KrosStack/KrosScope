package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution

/**
 * Builds the dependency between a consumer and the module that builds the JAR it declares, or the finding that
 * several modules build it or that the archive of the one that does is not there. A link is exact only when the content
 * of the archive matched the module.
 */
internal object JvmLibraryEdges {
    private val ambiguousProducer = ReasonCode("artifact.ambiguous-producer")
    private val missingOutput = ReasonCode("artifact.output-missing")

    fun link(archive: DeclaredArchive, producer: Module, content: ContentState): Relationship {
        val matched = content == ContentState.MATCHED
        val target = Resolution.Resolved(NodeRef.OfModule(producer.id), certaintyOf(archive, producer, matched))
        val evidence = if (matched) EvidenceClass.CONTENT else EvidenceClass.BUILD_MODEL
        val attributes = attributesOf(archive) + listOfNotNull(content.value?.let { "content" to it })
        return relationshipOf(archive, target, evidence, attributes)
    }

    fun ambiguous(archive: DeclaredArchive): Relationship = unresolved(archive, ambiguousProducer)

    fun outputMissing(archive: DeclaredArchive): Relationship = unresolved(archive, missingOutput)

    private fun unresolved(archive: DeclaredArchive, reason: ReasonCode): Relationship = relationshipOf(
        archive,
        Resolution.Unresolved(archive.fileName, reason),
        EvidenceClass.BUILD_MODEL,
        attributesOf(archive),
    )

    private fun relationshipOf(
        archive: DeclaredArchive,
        resolution: Resolution,
        evidence: EvidenceClass,
        attributes: Map<String, String>,
    ) = Relationship(
        from = archive.edge.from,
        kind = RelationKind.DEPENDS_ON,
        subKind = JvmLibraryLinks.SUB_KIND,
        resolution = resolution,
        evidenceClass = evidence,
        site = archive.edge.site,
        scope = archive.edge.scope,
        attributes = attributes,
    )

    private fun certaintyOf(archive: DeclaredArchive, producer: Module, matched: Boolean): Certainty {
        val declared = (archive.edge.resolution as? Resolution.Resolved)?.certainty
        val exact = matched && declared != Certainty.HEURISTIC && producer.certainty != Certainty.HEURISTIC
        return if (exact) Certainty.EXACT else Certainty.HEURISTIC
    }

    private fun attributesOf(archive: DeclaredArchive): Map<String, String> = listOfNotNull(
        archive.edge.attributes["configuration"]?.let { "configuration" to it },
        "file" to archive.fileName,
    ).toMap()
}
