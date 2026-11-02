package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.worker.WorkerModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A module and an unrelated module of the same request declare a Java type of the same name, one of them as a
 * generated stub. The module is analysed to the same facts as in a request of its own.
 */
class SameNameIsolationTest {
    private val contract = SyntheticSource(
        "clash/Contract.java",
        "java",
        "package clash;\n\npublic interface Contract {\n    void ping();\n\n" +
            "    public static abstract class Stub implements clash.Contract {\n    }\n}\n",
        SourceSetId("replaced-by-the-source-set"),
        "test.stub",
    )

    private val own = SampleWorkspace.module(
        "own",
        Platform.JVM,
        SampleWorkspace.sourceSet("own", "main", "clash/own", syntheticSources = listOf(contract)),
    )

    private val other = SampleWorkspace.module(
        "other",
        Platform.JVM,
        SampleWorkspace.sourceSet("other", "main", "clash/other"),
    )

    private val mid = SampleWorkspace.module(
        "mid",
        Platform.JVM,
        SampleWorkspace.sourceSet("mid", "main", "clash/mid"),
    )

    private fun ownBatch(modules: List<WorkerModule>): FactBatch =
        FactPass(WorkerIdentity.producer).batchSequence(modules).toList()
            .single { it.owner.key.endsWith(":own@main") }

    @Test
    fun `a module resolves its generated Java stub when it is analysed alone`() {
        val resolved = listOf(ownBatch(listOf(own))).relationships().filter { relationship ->
            val target = (relationship.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol
            target?.id?.value?.endsWith("clash/Contract#Stub#") == true
        }
        assertTrue(resolved.isNotEmpty(), "the stub does not resolve")
    }

    @Test
    fun `an unrelated module with a Java type of the same name changes nothing in the facts of a module`() {
        val alone = json(ownBatch(listOf(own)))
        assertEquals(alone, json(ownBatch(listOf(other, own))))
        assertEquals(alone, json(ownBatch(listOf(own, other))))
    }

    @Test
    fun `a module does not see the Java types of a module that only its dependency uses`() {
        val dependingOnMid = own.copy(dependencies = listOf("mid"))
        val midUsingOther = mid.copy(dependencies = listOf("other"))
        val alone = json(ownBatch(listOf(mid, dependingOnMid)))
        assertEquals(alone, json(ownBatch(listOf(other, midUsingOther, dependingOnMid))))
        assertEquals(alone, json(ownBatch(listOf(dependingOnMid, midUsingOther, other))))
    }

    private fun json(batch: FactBatch): String = CanonicalFacts.toJson(listOf(batch))
}
