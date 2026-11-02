package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.diagnostics
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.WorkerModule
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The classpath of a common source set is the one of the JVM and Android compilations that include it, so its
 * libraries hold Java classes. The compiler can read those only in a session with Java support.
 */
class CommonWithJvmClasspathTest {
    private val junitApi = Path.of(TestInfo::class.java.protectionDomain.codeSource.location.toURI()).toString()

    private fun module(platform: Platform): WorkerModule = SampleWorkspace.module(
        "shared",
        platform,
        SampleWorkspace.sourceSet("shared", "commonMain", "relations/commonjava/main"),
    ).copy(classpath = listOf(junitApi))

    private fun failures(platform: Platform) = analyzeAll(module(platform)).diagnostics()
        .filter { it.code.value.startsWith("kotlin.") && it.code.value.endsWith("failed") }
        .map { it.message }

    @Test
    fun `a common source set with a classpath reads Java classes of its libraries without compiler failures`() {
        assertEquals(emptyList(), failures(Platform.COMMON))
    }

    @Test
    fun `the references through a Java class of the classpath are resolved in the common source set`() {
        val edges = Edges("shared@commonMain ").of(analyzeAll(module(Platform.COMMON)))
        Edges.assertHas(
            edges,
            "rel/commonjava/Reporter#title(). references/read -> rel/commonjava/Reporter#info.",
            "rel/commonjava/describe(Reporter). calls -> rel/commonjava/Reporter#title().",
            "rel/commonjava/describe(Reporter). calls -> rel/commonjava/Reporter#tags().",
        )
    }

    @Test
    fun `expect declarations of a common source set with a classpath are still actualized by the platform ones`() {
        val common = RelationSamples.withClasspath(RelationSamples.common)
        val edges = Edges().of(analyzeAll(listOf(common, RelationSamples.jvm, RelationSamples.android)))
        Edges.assertHas(
            edges,
            "lib@jvmMain rel/kmp/currentPlatform(). actualizes -> lib@commonMain rel/kmp/currentPlatform().",
            "lib@androidMain rel/kmp/Clock# actualizes -> lib@commonMain rel/kmp/Clock#",
        )
    }

    @Test
    fun `the same source set analysed as JVM code gives the same answers`() {
        assertEquals(emptyList(), failures(Platform.JVM))
        val common = Edges("shared@commonMain ").of(analyzeAll(module(Platform.COMMON)))
        val jvm = Edges("shared@commonMain ").of(analyzeAll(module(Platform.JVM)))
        assertEquals(jvm.sorted(), common.sorted())
    }
}
