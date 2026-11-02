package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.edgesOf
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.problems
import io.github.krosstack.kscope.analyzer.gradle.statics.sourceSets
import io.github.krosstack.kscope.analyzer.gradle.statics.variantsOf
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidVariantsStaticTest {
    private val kotlinTree = StaticWorkspace("android-kotlin")
    private val groovyTree = StaticWorkspace("android-groovy")
    private val kotlin = kotlinTree.provided()
    private val groovy = groovyTree.provided()

    @AfterTest
    fun cleanUp() {
        kotlinTree.close()
        groovyTree.close()
    }

    @Test
    fun `several dimensions give every combination and combination source sets`() {
        val variants = kotlin.model.variants.filter { it.module.value == "tasktide//:multi" }.map { it.name }
        assertEquals(
            listOf(
                "freeGalaxyDebug",
                "freeGalaxyRelease",
                "freePlayDebug",
                "freePlayRelease",
                "paidGalaxyDebug",
                "paidGalaxyRelease",
                "paidPlayDebug",
                "paidPlayRelease",
            ),
            variants,
        )
        assertEquals(listOf("free", "freePlay", "main"), kotlin.sourceSets(":multi").keys.toList())
        assertEquals(listOf("freePlayDebug", "freePlayRelease"), kotlin.variantsOf(":multi", "freePlay"))
        assertEquals(4, kotlin.variantsOf(":multi", "free").size)
    }

    @Test
    fun `the compile sdk is read from a release call and from the version catalog`() {
        assertEquals("36", kotlin.module(":multi").attributes["android.compileSdk"])
        assertEquals("35", kotlin.module(":lib").attributes["android.compileSdk"])
    }

    @Test
    fun `a computed compile sdk is reported and not guessed`() {
        assertNull(kotlin.module(":odd").attributes["android.compileSdk"])
        val info = kotlin.problems("gradle.static.value-not-analysed").single()
        assertEquals(DiagnosticSeverity.INFO, info.severity)
        assertEquals("odd/build.gradle.kts", info.location?.artifact?.path?.value)
    }

    @Test
    fun `groovy build scripts give the same model as kotlin build scripts`() {
        val app = groovy.module(":app")
        assertEquals(listOf("com.android.application", "org.jetbrains.kotlin.android"), app.plugins)
        assertEquals("sample.app", app.androidNamespace)
        assertEquals(
            mapOf(
                "android.buildConfigField.SYNC_ENDPOINT" to "String",
                "android.buildConfigField.VERBOSE_LOGGING" to "boolean",
                "android.buildFeatures.buildConfig" to "true",
                "android.buildFeatures.viewBinding" to "true",
                "android.compileSdk" to "36",
                "android.flavorDimensions" to "tier",
            ),
            app.attributes,
        )
        assertEquals(
            listOf("demoDebug", "demoRelease", "fullDebug", "fullRelease"),
            groovy.model.variants.map { it.name },
        )
        assertEquals(listOf("debug", "demo", "main", "test"), groovy.sourceSets(":app").keys.toList())
        assertEquals(listOf("demoDebug", "fullDebug"), groovy.variantsOf(":app", "debug"))
    }

    @Test
    fun `groovy dependencies are edges of the module`() {
        val edge = groovy.edgesOf(":app").single()
        val target = NodeRef.OfExternal("maven:androidx.core:core-ktx")
        assertEquals(Resolution.Resolved(target, Certainty.EXACT), edge.resolution)
        assertEquals(mapOf("configuration" to "implementation", "version" to "1.18.0"), edge.attributes)
    }
}
