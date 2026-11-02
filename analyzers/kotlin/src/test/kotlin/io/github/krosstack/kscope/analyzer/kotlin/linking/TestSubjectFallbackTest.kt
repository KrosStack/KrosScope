package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class TestSubjectFallbackTest : TestSubjectBase() {
    @Test
    fun `a test with no matching name is bound to a top-level function of its module that it uses most`() {
        val format = draft.function(main, "p.app.formatDate")
        val other = draft.function(main, "p.app.parseDate")
        val dates = testClass("p.app.DatesTest")
        val method = draft.function(test, "p.app.DatesTest.formats", testFlag, dates)
        listOf(format, format, other).forEach { draft.relate(method, RelationKind.CALLS, it) }

        val edge = checkNotNull(subjectOf(dates))

        assertEquals(Resolution.Resolved(format.node, Certainty.HEURISTIC), edge.resolution)
    }

    @Test
    fun `the declarations of the own module come before the classes of other modules`() {
        val lib = draft.sourceSet(":lib", "main")
        draft.dependency(":app", ":lib")
        val model = draft.type(lib, "p.lib.Model")
        val construct = draft.function(lib, "p.lib.Model.copy", container = model)
        val checksum = draft.function(main, "p.app.checksum")
        val checksums = testClass("p.app.ChecksumsTest")
        val method = draft.function(test, "p.app.ChecksumsTest.payload", testFlag, checksums)
        listOf(construct, construct, construct, checksum, checksum).forEach {
            draft.relate(method, RelationKind.CALLS, it)
        }

        assertEquals(checksum.node, targetOf(checksums))
    }

    @Test
    fun `a test of a module that uses none of its code is bound to the class of another module it uses most`() {
        val lib = draft.sourceSet(":lib", "main")
        draft.dependency(":app", ":lib")
        val model = draft.type(lib, "p.lib.Model")
        val untitled = testClass("p.app.Untitled")
        val method = draft.function(test, "p.app.Untitled.run", testFlag, untitled)
        draft.relate(method, RelationKind.TYPE_USES, model)

        assertEquals(model.node, targetOf(untitled))
    }

    @Test
    fun `a tie between the classes a test references most leaves it without a subject`() {
        val first = draft.type(main, "p.app.First")
        val second = draft.type(main, "p.app.Second")
        val untitled = testClass("p.app.Untitled")
        val method = draft.function(test, "p.app.Untitled.run", testFlag, untitled)
        draft.relate(method, RelationKind.TYPE_USES, first)
        draft.relate(method, RelationKind.TYPE_USES, second)

        assertNull(subjectOf(untitled))
    }

    @Test
    fun `a test that references no workspace class has no subject`() {
        val untitled = testClass("p.app.Untitled")
        draft.function(test, "p.app.Untitled.run", testFlag, untitled)

        assertNull(subjectOf(untitled))
    }

    @Test
    fun `references to other tests or to generated classes do not make a subject`() {
        val helper = draft.type(test, "p.app.Helper")
        val generated = draft.type(main, "p.app.R", origin = SymbolOrigin.Generated("g", null))
        val untitled = testClass("p.app.Untitled")
        val method = draft.function(test, "p.app.Untitled.run", testFlag, untitled)
        draft.relate(method, RelationKind.TYPE_USES, helper)
        draft.relate(method, RelationKind.TYPE_USES, generated)

        assertNull(subjectOf(untitled))
    }
}
