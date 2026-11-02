package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.analyzer.schema.SymbolSite
import io.github.krosstack.kscope.analyzer.schema.TextIndex
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptInput
import io.github.krosstack.kscope.analyzer.schema.generator.specs.SpecTarget
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolDescriptor

/**
 * The symbol of each input of a generator in a build script, and its relationships to the spec files it reads. The
 * symbol belongs to the module of the script and to no source set, and sits at the call that gives the input. It
 * depends on the symbol of each spec file it reaches, or, when it reaches none, on nothing: the spec is then not in
 * the workspace. The path of the input is kept as the script writes it, never as a location on disk.
 */
internal class InputFacts(private val script: Artifact, private val placement: Placement, text: String) {
    private val site = SymbolSite(script.ref, placement.scope, TextIndex(text))
    private val occurrences = HashMap<String, Int>()

    fun of(input: ScriptInput, targets: List<SpecTarget>): List<Fact> {
        val symbol = symbolOf(input)
        val dependencies = resolutionsOf(input, targets).map { Fact.OfRelationship(dependency(symbol, it)) }
        return listOf(Fact.OfSymbol(symbol)) + dependencies
    }

    private fun resolutionsOf(input: ScriptInput, targets: List<SpecTarget>): List<Resolution> =
        if (targets.isEmpty()) {
            listOf(Resolution.Unresolved(input.path.text, ReasonCode(GeneratorKinds.PRODUCER_NOT_IN_WORKSPACE)))
        } else {
            targets.map { Resolution.Resolved(NodeRef.OfSymbol(it.symbol), Certainty.EXACT) }
        }

    private fun symbolOf(input: ScriptInput): Symbol = SchemaSymbol(
        id = placement.identity.idOf(descriptorOf(input)),
        kind = input.kind.symbolKind,
        name = input.path.name,
        qualifiedName = "${script.ref.path.value}#${input.kind.property}",
        signature = input.path.text,
        container = null,
        declaration = input.call,
        nameSpan = input.name,
        attributes = attributesOf(input),
        evidence = EvidenceClass.BUILD_MODEL,
    ).toSymbol(site)

    private fun attributesOf(input: ScriptInput): Map<String, String> =
        input.openApi?.generatorName?.let { mapOf(GeneratorKinds.GENERATOR_NAME_ATTRIBUTE to it) }.orEmpty()

    private fun descriptorOf(input: ScriptInput): SymbolDescriptor {
        val descriptor = SymbolDescriptor().meta(script.ref.path.value).meta(input.kind.property).term(input.path.text)
        val ordinal = occurrences.merge("${input.kind.property} ${input.path.text}", 1, Int::plus) ?: 1
        return if (ordinal == 1) descriptor else descriptor.term(ordinal.toString())
    }

    private fun dependency(from: Symbol, resolution: Resolution) = Relationship(
        from = NodeRef.OfSymbol(from.id),
        kind = RelationKind.DEPENDS_ON,
        subKind = GeneratorKinds.INPUT_SUB_KIND,
        resolution = resolution,
        evidenceClass = EvidenceClass.BUILD_MODEL,
        site = from.declaration,
        scope = from.scope,
    )
}
