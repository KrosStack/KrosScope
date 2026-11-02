package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.note
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.TextRange

/** A class name written at [range] of an XML file, to be linked with a relationship of [kind] and [subKind]. */
internal class ClassMention(val written: String, val kind: RelationKind, val subKind: String?, val range: TextRange)

/** The mention of a class by the symbol [from] that an element of the file stands for. */
internal class OwnedMention(val from: NodeRef, val mention: ClassMention)

/**
 * Turns the class names of one XML file into symbolic references of namespace `jvm.class`. Names of the platform and of
 * libraries, and relative names in a module without a namespace, are not references; each kind is counted in one note.
 * Text that is no class name, such as a manifest placeholder, is dropped.
 */
internal class ClassReferenceFacts(
    private val artifact: Artifact,
    private val namespace: String?,
    private val scope: Scope,
) {
    fun of(mentions: List<OwnedMention>): List<Fact> {
        val read = mentions.map { it to JvmClasses.read(it.mention.written, namespace) }
        val references = read.mapNotNull { (owned, written) ->
            (written as? WrittenClass.Named)?.let { referenceOf(owned, it) }
        }.distinct().map { Fact.OfSymbolicReference(it) }
        return references + notes(read.map { it.second })
    }

    private fun referenceOf(owned: OwnedMention, written: WrittenClass.Named) = SymbolicReference(
        from = owned.from,
        kind = owned.mention.kind,
        subKind = owned.mention.subKind,
        namespace = JvmClasses.NAMESPACE,
        key = written.qualifiedName,
        site = SourceLocation(artifact.ref, owned.mention.range),
        scope = scope,
        evidenceClass = EvidenceClass.TEXTUAL,
    )

    private fun notes(read: List<WrittenClass>): List<Fact> = listOfNotNull(
        note(read.count { it == WrittenClass.Framework }, "framework-classes") {
            "$it class names of the platform or of libraries are not linked"
        },
        note(read.count { it == WrittenClass.NeedsNamespace }, "namespace-unknown") {
            "$it class names are relative to a namespace that the module does not have"
        },
    )

    private fun note(count: Int, code: String, message: (Int) -> String): Fact? =
        if (count == 0) null else artifact.note(DiagnosticSeverity.INFO, "android.class.$code", message(count))
}
