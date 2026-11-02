package io.github.krosstack.kscope.benchmark.score.resource

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

private fun Symbol.asKind(id: String, category: SymbolCategory) = copy(kind = SymbolKind(id, category))

private fun surface(name: String, kind: String, generator: String, source: NodeRef? = null): Symbol =
    Graphs.symbol("", name, kind, 1, SymbolDescriptor().term(name))
        .copy(declaration = null, nameLocation = null, origin = SymbolOrigin.Generated(generator, source))

// A relationship of the kind from one node to a symbol, stated at a line of a file when a site is given.
private fun edge(from: NodeRef, to: Symbol, kind: RelationKind, evidence: EvidenceClass, site: Pair<String, Int>?) =
    Relationship(
        from = from,
        kind = kind,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(to.id), Certainty.EXACT),
        evidenceClass = evidence,
        site = site?.let { (path, line) -> SourceLocation(Graphs.ref(path), TextRange(line, 3, line, 20)) },
        scope = Scope(),
    )

private fun consumer(file: String, lines: List<Int>) = GoldAddress(file = file, kind = "consumer.kotlin", lines = lines)

private fun gold(id: String, relationshipClass: String, from: GoldAddress, to: GoldAddress, evidence: String) =
    GoldRelationship(id, relationshipClass, from, to, evidence)

class GeneratedSurfaceScorerTest {
    private val manifest = "app/src/main/AndroidManifest.xml"
    private val layoutFile = "app/src/main/res/layout/item_task.xml"
    private val navigation = "app/src/main/res/navigation/nav.xml"
    private val script = "app/build.gradle.kts"
    private val code = "app/src/main/kotlin/p/Main.kt"
    private val other = "app/src/main/kotlin/p/Other.kt"

    private fun resource(path: String, type: String, name: String) =
        Graphs.symbol(path, name, "android.resource.$type", 1, SymbolDescriptor().term(name))
            .asKind("android.resource.$type", SymbolCategory.RESOURCE)

    private val mainClass = Graphs.symbol(code, "p.Main", "kotlin.class", 5, Graphs.type("Main"))
    private val otherClass = Graphs.symbol(other, "p.Other", "kotlin.class", 3, Graphs.type("Other"))
    private val activity = Graphs.symbol(manifest, ".Main", "activity", 14, SymbolDescriptor().term("a"))
        .asKind("android.manifest.activity", SymbolCategory.COMPONENT).copy(name = ".Main")
    private val itemTask = resource(layoutFile, "layout", "item_task")
    private val nav = resource(navigation, "navigation", "nav")
    private val binding = surface("p.databinding.ItemTaskBinding", "java.class", "android.viewbinding")
    private val title = surface("p.databinding.ItemTaskBinding.title", "java.field", "android.viewbinding")
    private val endpoint = surface(
        "p.BuildConfig.ENDPOINT",
        "java.field",
        "android.buildconfig",
        NodeRef.OfArtifact(Graphs.ref(script)),
    )

    private val graph = FakeGraph(
        symbols = listOf(mainClass, otherClass, activity, itemTask, nav, binding, title, endpoint),
        artifacts = listOf(
            Graphs.artifact(manifest, "android.manifest"),
            Graphs.artifact(layoutFile, "android.resource.layout"),
            Graphs.artifact(navigation, "android.resource.navigation"),
            Graphs.artifact(script, "gradle.build-script"),
            Graphs.artifact(code),
            Graphs.artifact(other),
        ),
        modules = listOf(Graphs.module(":core", "core", Graphs.buildRoot("").id)),
        buildRoots = listOf(Graphs.buildRoot("")),
        relationships = relationships(),
    )

    private fun relationships(): List<Relationship> {
        val textual = EvidenceClass.TEXTUAL
        val convention = EvidenceClass.CONVENTION
        val compiler = EvidenceClass.COMPILER
        val main = NodeRef.OfSymbol(mainClass.id)
        val layout = NodeRef.OfSymbol(itemTask.id)
        val script = NodeRef.OfArtifact(Graphs.ref(script))
        return listOf(
            edge(NodeRef.OfSymbol(activity.id), mainClass, RelationKind.BINDS, textual, manifest to 14),
            edge(layout, mainClass, RelationKind.REFERENCES, textual, layoutFile to 11),
            edge(NodeRef.OfSymbol(nav.id), otherClass, RelationKind.REFERENCES, textual, navigation to 9),
            edge(layout, binding, RelationKind.GENERATES, convention, null),
            edge(layout, title, RelationKind.GENERATES, convention, null),
            edge(main, binding, RelationKind.IMPORTS, compiler, code to 3),
            edge(main, binding, RelationKind.REFERENCES, compiler, code to 9),
            edge(main, title, RelationKind.REFERENCES, compiler, code to 12),
            edge(script, endpoint, RelationKind.GENERATES, convention, null),
            edge(main, endpoint, RelationKind.REFERENCES, compiler, code to 7),
            edge(main, endpoint, RelationKind.CALLS, compiler, code to 7),
        )
    }

    private val index = GraphIndex(graph)
    private val resolver = GoldAddressResolver(graph)

    private val mainAddress = GoldAddress(
        module = ":core",
        sourceSet = "main",
        fqName = "p.Main",
        kind = "class",
        file = code,
        line = 5,
    )

    private val layoutAddress =
        GoldAddress(module = ":core", file = layoutFile, kind = "android.layout", name = "item_task")

    private val fieldAddress =
        GoldAddress(module = ":core", file = script, kind = "buildconfig.field", name = "ENDPOINT")

    private val navigationAddress =
        GoldAddress(module = ":core", file = navigation, kind = "android.navigation", name = "nav", line = 9)

    private fun manifestGold(evidence: String) = gold(
        "m:1",
        "manifest_component_to_class",
        GoldAddress(module = ":core", file = manifest, kind = "manifest.activity", name = ".Main", line = 14),
        mainAddress,
        evidence,
    )

    private fun ClassScore.matched() = score?.matched

    @Test
    fun `a manifest component is bound to its class and the evidence is compared with gold`() {
        val textual = ManifestComponentScorer(index, resolver).score(listOf(manifestGold("textual")))
        assertEquals(1, textual.matched())
        assertEquals(0, textual.evidenceMismatches)
        val buildModel = ManifestComponentScorer(index, resolver).score(listOf(manifestGold("build-model")))
        assertEquals(1, buildModel.matched())
        assertEquals(1, buildModel.evidenceMismatches, "Textual is not what gold expects, and nothing is relaxed")
        assertEquals(emptyMap(), buildModel.acceptedEvidence)
    }

    @Test
    fun `a component that no relationship binds is a miss and a binding that gold lacks is extra`() {
        val ghost = manifestGold("textual").copy(from = manifestGold("textual").from.copy(name = ".Ghost"))
        val score = ManifestComponentScorer(index, resolver).score(listOf(ghost))
        assertEquals(1, score.score?.misses?.size)
        assertEquals(1, score.score?.extras?.size)
        assertEquals(1, score.unmapped.size)
    }

    @Test
    fun `a layout reference is told by the line of the place that names the class`() {
        val layoutGold = gold(
            "l:1",
            "layout_class_reference",
            layoutAddress.copy(line = 11),
            mainAddress,
            "textual",
        )
        val navigationGold = layoutGold.copy(
            id = "l:2",
            from = navigationAddress,
            to = mainAddress.copy(fqName = "p.Other", file = other, line = 3),
        )
        val score = LayoutClassReferenceScorer(index, resolver).score(listOf(layoutGold, navigationGold))
        assertEquals(2, score.matched())
        assertEquals(emptyList(), score.score?.extras)
        val wrongLine = layoutGold.copy(from = layoutGold.from.copy(line = 12))
        assertEquals(1, LayoutClassReferenceScorer(index, resolver).score(listOf(wrongLine)).score?.misses?.size)
    }

    @Test
    fun `the consumers of a view binding class are the places that name the class and not those of its fields`() {
        val bindingGold = gold(
            "v:1",
            "viewbinding_layout_to_consumer",
            layoutAddress.copy(kind = "android.resource.layout", line = 1),
            consumer(code, listOf(3, 9)),
            "generated-artifact",
        )
        val score = ViewBindingScorer(index, resolver).score(listOf(bindingGold))
        assertEquals(2, score.matched())
        assertEquals(emptyList(), score.score?.extras, "The use of a field on line 12 is not a place gold lists")
        assertEquals(0, score.evidenceMismatches)
        assertEquals(mapOf("convention" to 2), score.acceptedEvidence)
    }

    @Test
    fun `a build config field is read where code names it and the evidence is that of the build script`() {
        val fieldGold = gold(
            "b:1",
            "buildconfig_field_to_consumer",
            fieldAddress.copy(line = 18),
            consumer(code, listOf(7)),
            "generated-artifact",
        )
        val score = BuildConfigScorer(index, resolver).score(listOf(fieldGold))
        assertEquals(1, score.matched(), "Two relationships on one line are one place")
        assertEquals(emptyList(), score.score?.extras)
        assertEquals(mapOf("convention" to 1), score.acceptedEvidence)
        val missing = fieldGold.copy(from = fieldGold.from.copy(name = "OTHER"))
        val unmapped = BuildConfigScorer(index, resolver).score(listOf(missing))
        assertEquals(1, unmapped.unmapped.size)
        assertEquals(1, unmapped.score?.misses?.size)
    }

    @Test
    fun `a gold line that no consumer reads is a miss`() {
        val fieldGold = gold(
            "b:2",
            "buildconfig_field_to_consumer",
            fieldAddress.copy(line = 18),
            consumer(code, listOf(7, 8)),
            "convention",
        )
        val score = BuildConfigScorer(index, resolver).score(listOf(fieldGold))
        assertEquals(1, score.matched())
        assertEquals(1, score.score?.misses?.size)
    }
}
