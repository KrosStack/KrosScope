package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.FileContext
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope

/** Where a reference occurs: [from] is the innermost declared symbol around it, [range] its character offsets. */
internal class ReferenceSite(val from: NodeRef, val range: IntRange, val hint: String)

private data class RelationshipKey(
    val from: NodeRef,
    val kind: RelationKind,
    val subKind: String?,
    val resolution: Resolution,
    val range: IntRange,
    val evidenceClass: EvidenceClass = EvidenceClass.COMPILER,
)

private val unresolvedCode = ReasonCode("kotlin.unresolved-reference")
private val unresolvedWithoutClasspathCode = ReasonCode("kotlin.unresolved-reference.no-classpath")
private val ambiguousCode = ReasonCode("kotlin.ambiguous-reference")
private val sharedIdentityCode = ReasonCode("identity.collision")

internal const val SYNTHETIC_MEMBER = "synthetic-member"

private fun targetKey(node: NodeRef): String = when (node) {
    is NodeRef.OfSymbol -> node.id.value
    is NodeRef.OfArtifact -> node.ref.path.value
    else -> ""
}

/**
 * Turns what the compiler answered for a reference into relationships of one file, under the resolution discipline:
 * a workspace target is exact, a library target is kept only where the shape allows it, an erroneous reference with a
 * single candidate is probable, one with several workspace candidates is ambiguous and one that nothing declares is
 * unresolved with a reason. A resolved target whose id another declaration shares is unresolved too: the id does not
 * say which declaration the compiler chose. Erroneous references that only fit library code are dropped like other
 * library references. Identical relationships are kept once.
 */
internal class ReferenceReporter(
    private val file: FileContext,
    private val hasClasspath: Boolean,
    private val collisions: IdentityCollisions,
) {
    private val relationships = LinkedHashMap<RelationshipKey, Relationship>()

    /** The relationships in the order of their sites, whatever order the compiler answered in. */
    fun relationships(): List<Relationship> = relationships.entries
        .sortedWith(compareBy({ it.key.range.first }, { it.key.range.last }, { it.key.kind }, { sortKey(it.key) }))
        .map { it.value }

    fun <S> report(site: ReferenceSite, shape: Shape, attempt: Attempt<S>, map: (S) -> Target?) {
        when (attempt) {
            is Attempt.Resolved -> reportResolved(site, shape, attempt.symbols.mapNotNull(map))
            is Attempt.Erroneous -> reportErroneous(site, shape, attempt.candidates, map)
            Attempt.Unavailable -> Unit
        }
    }

    private fun reportResolved(site: ReferenceSite, shape: Shape, targets: List<Target>) {
        val eligible = targets.distinctBy { it.node }.filter { isEligible(it, shape) }
        val (shared, unique) = eligible.partition { it is Target.Workspace && collisions.isShared(it.id) }
        unique.forEach { add(site, shape, it, Certainty.EXACT) }
        if (shared.isNotEmpty()) addUnresolved(site, shape, sharedIdentityCode)
    }

    private fun <S> reportErroneous(site: ReferenceSite, shape: Shape, candidates: List<S>, map: (S) -> Target?) {
        val targets = candidates.map(map)
        val only = targets.singleOrNull()?.takeIf { isEligible(it, shape) }
        when {
            candidates.isEmpty() -> addUnresolved(site, shape, unresolvedReason())
            only != null -> add(site, shape, only, Certainty.PROBABLE)
            targets.size > 1 && targets.any { it is Target.Workspace } -> addUnresolved(site, shape, ambiguousCode)
        }
    }

    /**
     * A member that a compiler plugin adds to [owner] and the standalone front end does not know: the reference stands
     * for the class, as a convention and not as the compiler's answer.
     */
    fun reportSyntheticMember(site: ReferenceSite, shape: Shape, owner: Target.Workspace) {
        val resolution = Resolution.Resolved(owner.node, Certainty.PROBABLE)
        put(site, shape.kindFor(owner), SYNTHETIC_MEMBER, resolution, EvidenceClass.CONVENTION)
    }

    private fun add(site: ReferenceSite, shape: Shape, target: Target, certainty: Certainty) {
        val subKind = (target as? Target.Workspace)?.subKind ?: shape.subKind
        val resolution = Resolution.Resolved(target.node, certainty)
        put(site, shape.kindFor(target), subKind, resolution)
    }

    private fun addUnresolved(site: ReferenceSite, shape: Shape, reason: ReasonCode) {
        put(site, shape.kindFor(null), shape.subKind, Resolution.Unresolved(site.hint, reason))
    }

    private fun sortKey(key: RelationshipKey): String {
        val from = (key.from as? NodeRef.OfSymbol)?.id?.value.orEmpty()
        val target = when (val resolution = key.resolution) {
            is Resolution.Resolved -> "${targetKey(resolution.target)}|${resolution.certainty}"
            is Resolution.Unresolved -> "${resolution.reason.value}|${resolution.hint}"
            is Resolution.NotAnalysed -> resolution.reason.value
        }
        return "${key.subKind.orEmpty()}|$from|$target"
    }

    private fun isEligible(target: Target, shape: Shape) = target !is Target.Library || shape.allowsLibrary

    private fun unresolvedReason() = if (hasClasspath) unresolvedCode else unresolvedWithoutClasspathCode

    private fun put(
        site: ReferenceSite,
        kind: RelationKind,
        subKind: String?,
        resolution: Resolution,
        evidenceClass: EvidenceClass = EvidenceClass.COMPILER,
    ) {
        val key = RelationshipKey(site.from, kind, subKind, resolution, site.range, evidenceClass)
        relationships.getOrPut(key) {
            Relationship(
                from = site.from,
                kind = kind,
                subKind = subKind,
                resolution = resolution,
                evidenceClass = evidenceClass,
                site = file.location(site.range),
                scope = Scope(sourceSet = file.owner.sourceSet),
            )
        }
    }
}
