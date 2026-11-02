package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.analyzer.kotlin.worker.Golden
import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyze
import io.github.krosstack.kscope.analyzer.kotlin.worker.symbols
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SymbolFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinMultiplatformDeclarationsTest {
    private val common = SampleWorkspace.module(
        "lib:commonMain",
        Platform.COMMON,
        SampleWorkspace.sourceSet("lib", "commonMain", "kmp/commonMain"),
    )
    private val jvm = SampleWorkspace.module(
        "lib:jvmMain",
        Platform.JVM,
        SampleWorkspace.sourceSet("lib", "jvmMain", "kmp/jvmMain"),
    ).copy(dependsOn = listOf("lib:commonMain"))
    private val android = SampleWorkspace.module(
        "lib:androidMain",
        Platform.ANDROID,
        SampleWorkspace.sourceSet("lib", "androidMain", "kmp/androidMain"),
    ).copy(dependsOn = listOf("lib:commonMain"))

    private fun analyzeAll(): List<FactBatch> = analyze(common, jvm, android)

    @Test
    fun `expect and actual declarations across source sets match the golden output`() {
        Golden.assertMatches("kmp-expect-actual.jsonl", Golden.facts(analyzeAll()))
    }

    @Test
    fun `expect and actual declarations share a descriptor and differ by source set`() {
        val symbols = analyzeAll().symbols().filter { it.qualifiedName == "sample.currentPlatform" }
        assertEquals(setOf(SymbolFlag.EXPECT), symbols.single { it.id.value.contains(" commonMain ") }.flags)
        assertTrue(symbols.single { it.id.value.contains(" jvmMain ") }.flags.contains(SymbolFlag.ACTUAL))
        assertEquals(3, symbols.map { it.id }.distinct().size)
    }

    @Test
    fun `members of an expect class are expect as well`() {
        val members = analyzeAll().symbols().filter { it.id.value.contains(" commonMain ") && it.container != null }
        val expectClassMembers = members.filter { it.qualifiedName?.startsWith("sample.PlatformInfo.") == true }
        assertTrue(expectClassMembers.isNotEmpty() && expectClassMembers.all { SymbolFlag.EXPECT in it.flags })
    }

    @Test
    fun `one batch is produced per source set`() {
        val owners = analyzeAll().map { it.owner.key }.sorted()
        assertEquals(listOf("sample//:lib@androidMain", "sample//:lib@commonMain", "sample//:lib@jvmMain"), owners)
    }
}
