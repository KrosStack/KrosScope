package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.ContextPlanner
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.context.fragment.SourceDocuments
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.context.token.TokenCosts
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextRole

internal class MixedSelectorFixture {
    private val lines = listOf("class Box {", " fun first() = \"α🙂漢字\"", " fun second() = \"```\"", "}", "")
    private val sourceText = lines.joinToString("\n")
    private val source = sourceArtifact("source/Box", sourceText)
    private val box = declaration("Box", source, TextRange(1, 1, 5, 1), "class Box")
    private val first = declaration("first", source, TextRange(2, 2, 2, lines[1].length + 1), "fun first()").copy(
        container = box.id,
    )
    private val second = declaration("second", source, TextRange(3, 2, 3, lines[2].length + 1), "fun second()").copy(
        container = box.id,
    )
    private val generated = sourceArtifact("generated/Surface", "SECRET BODY").copy(
        generated = GeneratedInfo("test.generator", NodeRef.OfSymbol(box.id)),
    )
    private val surface = declaration("Surface", generated, TextRange(1, 1, 1, 12), "class Surface").copy(
        origin = SymbolOrigin.Generated("test.generator", NodeRef.OfSymbol(box.id)),
    )
    private val testText = "fun checks() = first()\n"
    private val testFile = sourceArtifact("tests/Box", testText)
    private val test = declaration("checks", testFile, TextRange(1, 1, 2, 1), "fun checks()")
    private val generation = Relationship(
        NodeRef.OfSymbol(box.id),
        RelationKind.GENERATES,
        null,
        Resolution.Resolved(NodeRef.OfSymbol(surface.id), Certainty.EXACT),
        EvidenceClass.CONVENTION,
        null,
        Scope(),
    )
    private val unresolved = generation.copy(
        from = NodeRef.OfSymbol(first.id),
        kind = RelationKind.CALLS,
        resolution = Resolution.Unresolved("External", ReasonCode("test.external")),
    )
    private val fixture = ContextFixture(
        listOf(source, generated, testFile),
        listOf(box, first, second, surface, test),
        mapOf(source.ref to sourceText, generated.ref to "SECRET BODY", testFile.ref to testText),
        listOf(generation, unresolved),
    )
    val graph = ContextGraph(fixture)
    val estimator = TokenEstimators.create()
    val input = ContextInput(
        "inspect α🙂漢字 boundaries",
        listOf(
            ContextCandidate(NodeRef.OfArtifact(source.ref), 1.0, ContextRole.EDIT),
            ContextCandidate(NodeRef.OfSymbol(box.id), 0.8, ContextRole.EDIT),
            ContextCandidate(NodeRef.OfSymbol(first.id), 0.4),
            ContextCandidate(NodeRef.OfSymbol(second.id), 0.4),
            ContextCandidate(NodeRef.OfSymbol(surface.id), 0.3, ContextRole.BOUNDARY),
            ContextCandidate(NodeRef.OfArtifact(testFile.ref), 0.1, ContextRole.TEST),
        ),
        boundaries = listOf(generation),
        tests = listOf(NodeRef.OfArtifact(testFile.ref)),
        unresolved = listOf(unresolved),
    )

    fun offers(level: ContextLevel): List<ContextOffer> {
        val renderer = FragmentRenderer(graph, SourceDocuments(fixture), TokenCosts(estimator))
        return ContextPlanner(graph).plan(input, level).candidates.flatMap { renderer.render(it, level).offers }
    }

    fun merge(): FragmentMerge = FragmentMerge(graph, SourceDocuments(fixture), TokenCosts(estimator))
}
