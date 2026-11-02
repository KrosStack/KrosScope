package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.Golden
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The serialization and parcelize plugins add `serializer()` and `CREATOR` to annotated classes. The analyzer runs
 * without the plugins, so those members do not resolve; the reference then stands for the annotated class.
 */
class SyntheticMemberRelationshipsTest {
    private val batches by lazy { analyzeAll(listOf(RelationSamples.plugins)) }
    private val edges by lazy { Edges("plugins@main ").of(batches) }
    private val usage = "rel/plugins/Usage#"
    private val javaUsage = "rel/plugins/JavaUsage#"
    private val unresolved = "kotlin.unresolved-reference.no-classpath"

    @Test
    fun `serializer on a serializable class is a probable reference to the class`() {
        Edges.assertHas(
            edges,
            "${usage}ticketSerializer(). calls/synthetic-member -> rel/plugins/Ticket# ~probable",
            "${usage}objectSerializer(). calls/synthetic-member -> rel/plugins/Settings# ~probable",
        )
    }

    @Test
    fun `serializer on the companion or without a receiver is a reference to the class as well`() {
        Edges.assertHas(
            edges,
            "${usage}companionSerializer(). calls/synthetic-member -> rel/plugins/Ticket# ~probable",
            "rel/plugins/Ticket#Companion#codec(). calls/synthetic-member -> rel/plugins/Ticket# ~probable",
        )
    }

    @Test
    fun `CREATOR on a parcelize class is a probable reference to the class from Kotlin and from Java`() {
        Edges.assertHas(
            edges,
            "${usage}passCreator(). references/synthetic-member -> rel/plugins/Pass# ~probable",
            "${javaUsage}creator(). references/synthetic-member -> rel/plugins/Pass# ~probable",
        )
    }

    @Test
    fun `the same names on classes without the annotation stay unresolved`() {
        Edges.assertHas(
            edges,
            "${usage}plainSerializer(). calls -> ?serializer ($unresolved)",
            "${usage}plainCreator(). references -> ?CREATOR ($unresolved)",
            "${usage}passSerializer(). calls -> ?serializer ($unresolved)",
            "${javaUsage}plainCreator(). references -> ?CREATOR ($unresolved)",
        )
    }

    @Test
    fun `a synthetic member is convention evidence, not the compiler's answer`() {
        val synthetic = batches.relationships().filter { it.subKind == "synthetic-member" }
        assertEquals(6, synthetic.size)
        synthetic.forEach {
            assertEquals(EvidenceClass.CONVENTION, it.evidenceClass)
            assertEquals(Certainty.PROBABLE, (it.resolution as Resolution.Resolved).certainty)
        }
        val others = batches.relationships().filter { it.subKind != "synthetic-member" }
        assertTrue(others.all { it.evidenceClass == EvidenceClass.COMPILER })
    }

    @Test
    fun `the output matches the golden file`() =
        Golden.assertMatches("relationships-plugins.jsonl", Golden.relationships(batches))
}
