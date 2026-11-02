package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.diagnostics
import io.github.krosstack.kscope.model.DiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnresolvedRelationshipsTest {
    private val withoutClasspath by lazy { Edges("unresolved@main ").of(analyzeAll(RelationSamples.unresolved)) }
    private val withClasspath by lazy {
        Edges("unresolved@main ").of(analyzeAll(RelationSamples.withClasspath(RelationSamples.unresolved)))
    }
    private val holder = "rel/unresolved/Holder#"
    private val javaHolder = "rel/unresolved/JavaHolder#"
    private val noClasspath = "kotlin.unresolved-reference.no-classpath"
    private val unresolved = "kotlin.unresolved-reference"

    @Test
    fun `a reference to a name that nothing declares is unresolved with the reason`() {
        Edges.assertHas(
            withClasspath,
            "${holder}unknownCall(). calls -> ?notDefined ($unresolved)",
            "${holder}unknown. type-uses -> ?Missing ($unresolved)",
            "${holder}unknownMember(Holder). references -> ?noSuchMember ($unresolved)",
        )
    }

    @Test
    fun `the implicit parameter of a lambda is not reported when the call that takes the lambda is unresolved`() {
        Edges.assertHas(withClasspath, "${holder}lambdaOfUnresolved(). calls -> ?notDefined ($unresolved)")
        assertTrue(withClasspath.none { it.contains("?it ") })
    }

    @Test
    fun `an unresolved import is an unresolved relationship of the file`() {
        val kotlinFile = "file:relations/unresolved/main/kotlin/rel/unresolved/Holder.kt"
        val javaFile = "file:relations/unresolved/main/java/rel/unresolved/JavaHolder.java"
        Edges.assertHas(
            withClasspath,
            "$kotlinFile imports -> ?missing.pkg.Imported ($unresolved)",
            "$javaFile imports -> ?missing.pkg.JavaImported ($unresolved)",
        )
    }

    @Test
    fun `a module without a classpath names the missing classpath as the reason`() {
        Edges.assertHas(
            withoutClasspath,
            "${holder}unknownCall(). calls -> ?notDefined ($noClasspath)",
            "${holder}unknown. type-uses -> ?Missing ($noClasspath)",
        )
        assertTrue(withoutClasspath.none { it.endsWith("($unresolved)") })
        assertTrue(withClasspath.none { it.endsWith("($noClasspath)") })
    }

    @Test
    fun `a call that fits several candidates is ambiguous without a target`() {
        Edges.assertHas(
            withClasspath,
            "${holder}ambiguous(). calls -> ?choose (kotlin.ambiguous-reference)",
            "${javaHolder}overloaded(). calls -> ?pick (kotlin.ambiguous-reference)",
        )
    }

    @Test
    fun `an erroneous call that only fits library declarations is dropped like any library reference`() {
        assertTrue(withClasspath.none { it.startsWith("${holder}onlyLibraryCandidates().") })
    }

    @Test
    fun `an erroneous call with exactly one candidate is probable`() {
        Edges.assertHas(withClasspath, "${holder}probable(). calls -> rel/unresolved/single(Int). ~probable")
    }

    @Test
    fun `a fully qualified library name is not reported as unresolved`() {
        assertTrue(withClasspath.none { it.startsWith("${holder}qualified().") })
    }

    @Test
    fun `Java references to missing classes are unresolved too`() {
        Edges.assertHas(
            withClasspath,
            "${javaHolder}absent. type-uses -> ?Absent ($unresolved)",
            "${javaHolder}absent. calls -> ?Absent ($unresolved)",
            "${javaHolder}call(). calls -> ?notDefined ($unresolved)",
            "${javaHolder}call(). references -> rel/unresolved/Holder#",
        )
    }

    @Test
    fun `a file with syntax errors keeps the references outside the damaged part`() {
        val batches = analyzeAll(RelationSamples.broken)
        Edges.assertHas(
            Edges("broken@main ").of(batches),
            "rel/broken/Damaged#fine(Target). calls -> rel/broken/Target#ok().",
            "rel/broken/Damaged#after(Target). calls -> rel/broken/Target#ok().",
            "rel/broken/trailing(Target). calls -> rel/broken/Target#ok().",
        )
        val diagnostic = batches.diagnostics().single()
        assertEquals("kotlin.syntax-error", diagnostic.code.value)
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
    }
}
