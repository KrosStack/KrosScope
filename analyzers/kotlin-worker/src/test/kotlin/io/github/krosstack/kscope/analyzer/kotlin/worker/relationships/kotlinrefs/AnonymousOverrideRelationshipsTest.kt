package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.RelationSamples
import kotlin.test.Test
import kotlin.test.assertTrue

/** The members of an object expression are not symbols, so their overrides belong to the symbol around the object. */
class AnonymousOverrideRelationshipsTest {
    private val lang by lazy { Edges("lang@main ").of(analyzeAll(listOf(RelationSamples.lang))) }
    private val inherited by lazy { Edges("inherit-app@main ").of(analyzeAll(RelationSamples.inheritance)) }
    private val run = "rel/lang/User#run(Point,Box)."

    @Test
    fun `an overriding member of an object expression is an override of the enclosing symbol`() {
        Edges.assertHas(
            lang,
            "$run overrides/anonymous -> rel/lang/Shape#area.",
            "$run overrides/anonymous -> rel/lang/Shape#scale(Int).",
        )
    }

    @Test
    fun `an object expression of a function overrides the members of an interface of another module`() {
        Edges.assertHas(
            inherited,
            "rel/inheritapp/anonymous(). overrides/anonymous -> inherit-lib@main rel/inherit/Repository#load(Int).",
            "rel/inheritapp/anonymous(). overrides/anonymous -> inherit-lib@main rel/inherit/Repository#count().",
        )
    }

    @Test
    fun `members of a named class are overrides of their own symbols and not anonymous`() {
        assertTrue(lang.none { " overrides/anonymous -> " in it && !it.startsWith(run) }, "$lang")
    }
}
