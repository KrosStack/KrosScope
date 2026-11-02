package io.github.krosstack.kscope.analyzer.android.manifest

import io.github.krosstack.kscope.analyzer.android.linking.ClassReferenceFacts
import io.github.krosstack.kscope.analyzer.android.linking.JvmClasses
import io.github.krosstack.kscope.analyzer.android.linking.OwnedMention
import io.github.krosstack.kscope.analyzer.android.linking.WrittenClass
import io.github.krosstack.kscope.analyzer.android.note
import io.github.krosstack.kscope.analyzer.android.resources.ResourceSymbols
import io.github.krosstack.kscope.analyzer.android.resources.symbolName
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.Visibility

internal const val EXPORTED_ATTRIBUTE = "android.manifest.exported"
internal const val ACTIONS_ATTRIBUTE = "android.manifest.actions"
internal const val CLASS_ATTRIBUTE = "android.manifest.class"

/**
 * The facts of one manifest: a symbol for the application, each component and each declared permission, the references
 * from them to the classes that implement them, and the references from their attributes to resources.
 */
internal class ManifestFacts(
    private val artifact: Artifact,
    private val module: Module,
    sourceSet: SourceSet,
    private val parsed: ParsedManifest,
) {
    private val scope = Scope(sourceSet = sourceSet.id)
    private val sourceSetName = sourceSet.symbolName()
    private val namespace = module.androidNamespace ?: parsed.packageName

    private class Declared(val component: ManifestComponent, val id: SymbolId, val implementing: String?)

    fun facts(): List<Fact> {
        val declared = parsed.components.map(::declare).distinctBy { it.id }
        return declared.map { Fact.OfSymbol(symbolOf(it)) } +
            classReferences(declared) + resourceReferences(declared) + notes(declared)
    }

    private fun declare(component: ManifestComponent): Declared {
        val implementing = component.classMention?.let { qualifiedNameOf(it.written) }
        val id = ManifestSymbols.idOf(module.id, sourceSetName, component.tag, nameOf(component))
        return Declared(component, id, implementing)
    }

    // A component is identified by the class it is named for, or by its name when that is no class of the workspace.
    private fun nameOf(component: ManifestComponent): String {
        val written = component.written ?: return component.tag
        return if (component.isPermission) written else qualifiedNameOf(written) ?: written
    }

    private fun qualifiedNameOf(written: String): String? =
        (JvmClasses.read(written, namespace) as? WrittenClass.Named)?.qualifiedName

    private fun symbolOf(declared: Declared): Symbol {
        val component = declared.component
        val name = component.written ?: component.tag
        return Symbol(
            id = declared.id,
            kind = ManifestSymbols.kindOf(component.tag),
            name = name,
            qualifiedName = null,
            signature = "${component.tag} $name".trim(),
            container = null,
            visibility = Visibility.PUBLIC,
            flags = emptySet(),
            origin = SymbolOrigin.Source,
            declaration = SourceLocation(artifact.ref, component.declaration),
            nameLocation = component.nameRange?.let { SourceLocation(artifact.ref, it) },
            documentation = null,
            scope = scope,
            evidenceClass = EvidenceClass.STRUCTURAL,
            certainty = Certainty.EXACT,
            attributes = attributesOf(declared),
        )
    }

    private fun attributesOf(declared: Declared): Map<String, String> {
        val component = declared.component
        return listOfNotNull(
            component.exported?.let { EXPORTED_ATTRIBUTE to it },
            component.actions.takeIf { it.isNotEmpty() }?.let { ACTIONS_ATTRIBUTE to it.joinToString(",") },
            declared.implementing?.let { CLASS_ATTRIBUTE to it },
        ).toMap()
    }

    private fun classReferences(declared: List<Declared>): List<Fact> {
        val mentions = declared.mapNotNull { item ->
            item.component.classMention?.let { OwnedMention(NodeRef.OfSymbol(item.id), it) }
        }
        return ClassReferenceFacts(artifact, namespace, scope).of(mentions)
    }

    private fun resourceReferences(declared: List<Declared>): List<Fact> = declared.flatMap { item ->
        item.component.uses.resources.map { use ->
            SymbolicReference(
                from = NodeRef.OfSymbol(item.id),
                kind = RelationKind.REFERENCES,
                subKind = null,
                namespace = ResourceSymbols.NAMESPACE,
                key = use.target.reference,
                site = SourceLocation(artifact.ref, use.range),
                scope = scope,
                evidenceClass = EvidenceClass.TEXTUAL,
            )
        }
    }.distinct().map { Fact.OfSymbolicReference(it) }

    private fun notes(declared: List<Declared>): List<Fact> {
        val externals = declared.sumOf { it.component.uses.externals }
        return listOfNotNull(
            externals.takeIf { it > 0 }?.let {
                artifact.note(
                    DiagnosticSeverity.INFO,
                    "android.resource.external-references",
                    "$it references to resources of the platform or of other packages are not linked",
                )
            },
            parsed.problem?.let {
                artifact.note(
                    DiagnosticSeverity.WARNING,
                    "android.manifest.xml-malformed",
                    "the file could not be read to its end: $it",
                )
            },
        )
    }
}
