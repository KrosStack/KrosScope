package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.TargetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinMultiplatformMappingTest {
    private val sample = SampleModel.load("kmp-android-library.json")

    private fun targetNames(ids: List<TargetId>) = ids.map { it.value.substringAfter("#target/") }

    @Test
    fun `only projects with plugins or sources become modules`() {
        assertEquals(listOf(":core:model", ":data:db", ":ui:shared"), sample.model.modules.map { it.path })
        assertEquals(listOf("tasktide//"), sample.model.buildRoots.map { it.id.value })
    }

    @Test
    fun `a multiplatform module with the android library target has both traits and its namespace`() {
        val module = sample.module(":core:model")
        assertEquals(setOf(ModuleTrait.KOTLIN_MULTIPLATFORM, ModuleTrait.ANDROID_LIBRARY), module.traits)
        assertEquals("sample.tasktide.core.model", module.androidNamespace)
        assertTrue("com.android.kotlin.multiplatform.library" in module.plugins)
        assertEquals(EvidenceClass.BUILD_MODEL, module.evidenceClass)
        assertEquals(Certainty.EXACT, module.certainty)
        assertEquals("core/model", module.directory.value)
    }

    @Test
    fun `targets are the declared ones and the metadata target is not one of them`() {
        val targets = sample.model.targets.filter { it.module == sample.module(":core:model").id }
        assertEquals(
            listOf("android" to Platform.ANDROID, "desktop" to Platform.JVM),
            targets.map { it.name to it.platform },
        )
        assertEquals("tasktide//:core:model#target/android", targets.first().id.value)
    }

    @Test
    fun `the android library target of a multiplatform module has no variants`() {
        assertTrue(sample.model.variants.isEmpty())
    }

    @Test
    fun `source sets with a directory are modelled with their dependsOn edges`() {
        val sourceSets = sample.sourceSets(":core:model")
        val names = listOf("androidMain", "commonMain", "commonTest", "desktopMain", "desktopTest")
        assertEquals(names, sourceSets.keys.toList())
        val desktopMain = sourceSets.getValue("desktopMain")
        assertEquals(listOf("tasktide//:core:model@commonMain"), desktopMain.dependsOn.map { it.value })
        val desktopTest = sourceSets.getValue("desktopTest")
        assertEquals(listOf("tasktide//:core:model@commonTest"), desktopTest.dependsOn.map { it.value })
        assertTrue(sourceSets.getValue("commonMain").dependsOn.isEmpty())
    }

    @Test
    fun `production source sets fed only by generated code are kept before the first build`() {
        val fresh = SampleDirectories.without(Regex("/work/tasktide/ui/shared/build/.*"))
        val expected = listOf("androidMain", "commonMain", "desktopMain")
        assertEquals(expected, sample.sourceSets(":ui:shared").keys.toList())
        assertEquals(expected, fresh.sourceSets(":ui:shared").keys.toList())
        assertEquals(
            listOf("tasktide//:ui:shared@commonMain"),
            fresh.sourceSets(":ui:shared").getValue("desktopMain").dependsOn.map { it.value },
        )
    }

    @Test
    fun `a module whose only directories are tests gets no production source set`() {
        val testsOnly = SampleDirectories.without(Regex("/work/tasktide/core/model/(src/[a-z]+Main|build)/.*"))
        assertEquals(listOf("commonTest", "desktopTest"), testsOnly.sourceSets(":core:model").keys.toList())
    }

    @Test
    fun `a module without any directory gets no source set`() {
        val empty = SampleDirectories.without(Regex("/work/tasktide/ui/shared/(src|build)/.*"))
        assertEquals(emptyList(), empty.sourceSets(":ui:shared").keys.toList())
    }

    @Test
    fun `source sets know the targets they are compiled for and their role`() {
        val sourceSets = sample.sourceSets(":core:model")
        assertEquals(listOf("android", "desktop"), targetNames(sourceSets.getValue("commonMain").targets))
        assertEquals(listOf("desktop"), targetNames(sourceSets.getValue("desktopMain").targets))
        assertEquals(SourceSetRole.MAIN, sourceSets.getValue("commonMain").role)
        assertEquals(SourceSetRole.UNIT_TEST, sourceSets.getValue("desktopTest").role)
    }
}
