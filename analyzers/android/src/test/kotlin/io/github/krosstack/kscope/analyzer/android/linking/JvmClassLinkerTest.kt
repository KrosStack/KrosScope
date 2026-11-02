package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.answerKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class JvmClassLinkerTest {
    private val tree = SampleTree("ui")
    private val mainActivity = "sample.droid.app.MainActivity"

    @Test
    fun `a class that one source set declares is the exact answer with textual evidence`() {
        val answer = ClassAnswers(tree, listOf(declared(mainActivity, ":app", "main"))).of(mainActivity)
        assertEquals(":app@main:sample/droid/app/MainActivity#", answer.targetText())
        assertEquals(Certainty.EXACT, answer.certaintyOf())
        assertEquals(EvidenceClass.TEXTUAL, answer.evidenceClass)
        assertEquals(RelationKind.BINDS, answer.kind)
    }

    @Test
    fun `every reference gets exactly one answer, found or not`() {
        val answers = ClassAnswers(tree, emptyList())
        assertTrue(answers.references.size > 10)
        assertEquals(answers.references.map { it.answerKey() }, answers.relationships.map { it.answerKey() })
    }

    @Test
    fun `a class that nothing declares is unresolved with a reason`() {
        val answer = ClassAnswers(tree, emptyList()).of("sample.droid.app.UiApp")
        assertEquals(
            Resolution.Unresolved("sample.droid.app.UiApp", ReasonCode("jvm.class.not-found")),
            answer.resolution,
        )
    }

    @Test
    fun `several declarations of a class are a probable answer that prefers the shared source set`() {
        val answers = ClassAnswers(
            tree,
            listOf(
                declared("sample.droid.app.SyncService", ":app", "debug"),
                declared("sample.droid.app.SyncService", ":app", "main"),
            ),
        )
        val answer = answers.of("sample.droid.app.SyncService")
        assertEquals(":app@main:sample/droid/app/SyncService#", answer.targetText())
        assertEquals(Certainty.PROBABLE, answer.certaintyOf())
        assertEquals(mapOf("jvm.class.candidates" to "2"), answer.attributes)
    }

    @Test
    fun `a class of a module that the referencing module depends on is found when its own module has none`() {
        val answer = ClassAnswers(tree, listOf(declared("sample.droid.app.BootReceiver", ":lib", "main")))
            .of("sample.droid.app.BootReceiver")
        assertEquals(":lib@main:sample/droid/app/BootReceiver#", answer.targetText())
        assertEquals(Certainty.EXACT, answer.certaintyOf())
    }

    @Test
    fun `the module of the reference wins over the modules it depends on`() {
        val answers = ClassAnswers(
            tree,
            listOf(
                declared("sample.droid.app.BootReceiver", ":lib", "main"),
                declared("sample.droid.app.BootReceiver", ":app", "main"),
            ),
        )
        val answer = answers.of("sample.droid.app.BootReceiver")
        assertEquals(":app@main:sample/droid/app/BootReceiver#", answer.targetText())
        assertEquals(Certainty.EXACT, answer.certaintyOf())
    }

    @Test
    fun `a module that does not depend on the declaring module does not see its classes`() {
        val isolated = SampleTree("ui", model = DroidModel.standard(withDependencies = false))
        val answers = ClassAnswers(isolated, listOf(declared("sample.droid.app.BootReceiver", ":lib", "main")))
        assertTrue(answers.relationships.all { it.resolution is Resolution.Unresolved })
    }

    @Test
    fun `only types answer a name, not a type alias nor a function of the same name`() {
        val alias = SymbolKind("kotlin.typealias", SymbolCategory.TYPE)
        val function = SymbolKind("kotlin.function", SymbolCategory.CALLABLE)
        val answers = ClassAnswers(
            tree,
            listOf(
                declared("sample.droid.app.UiApp", ":app", "main", alias),
                declared("sample.droid.app.SyncService", ":app", "main", function),
            ),
        )
        assertTrue(answers.of("sample.droid.app.UiApp").resolution is Resolution.Unresolved)
        assertTrue(answers.of("sample.droid.app.SyncService").resolution is Resolution.Unresolved)
    }

    @Test
    fun `a nested class is found under its dotted name`() {
        val answers = ClassAnswers(tree, listOf(declared("sample.droid.app.Outer.Inner", ":app", "main")))
        assertEquals(Certainty.EXACT, answers.of("sample.droid.app.Outer.Inner").certaintyOf())
    }

    @Test
    fun `a reference without a source set cannot be placed and is not analysed`() {
        val reference = SymbolicReference(
            from = NodeRef.OfModule(ModuleId("repo//:app")),
            kind = RelationKind.BINDS,
            subKind = null,
            namespace = "jvm.class",
            key = "sample.droid.app.UiApp",
            site = null,
            scope = Scope(),
            evidenceClass = EvidenceClass.TEXTUAL,
        )
        val out = mutableListOf<Fact>()
        JvmClassLinker().link(FakeGraph(listOf(Fact.OfSymbolicReference(reference)), tree.model)) { out += it.facts }
        val answer = out.filterIsInstance<Fact.OfRelationship>().single().value
        assertEquals(
            Resolution.NotAnalysed("sample.droid.app.UiApp", ReasonCode("jvm.class.scope-unknown")),
            answer.resolution,
        )
    }
}
