package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.build.StructuralBuildModelProvider
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A provider that executes nothing is not asked again while its inputs, the build logic included, are unchanged. */
class BuildLogicFingerprintTest {
    private class CountingProvider : BuildModelProvider {
        private val delegate = StructuralBuildModelProvider()
        override val producer = ProducerRef("test.counting", "1")
        override val requiresTrust = false
        var calls = 0
            private set

        override fun provide(request: BuildModelRequest): BuildModelResult {
            calls++
            return delegate.provide(request)
        }
    }

    private val provider = CountingProvider()
    private val workspace = TestWorkspace().also { files ->
        files.write("settings.gradle.kts", "include(\":app\")\n")
        files.write("app/build.gradle.kts", "plugins {}\n")
        files.write("app/src/main/kotlin/Main.kt", "class Main\n")
        files.write("build-logic/settings.gradle.kts", "rootProject.name = \"build-logic\"\n")
        files.write("build-logic/src/main/kotlin/ConventionPlugin.kt", "class ConventionPlugin\n")
        files.write("buildSrc/src/main/kotlin/Helper.kt", "class Helper\n")
    }
    private val engine = workspace.engine(coreRegistry(providers = listOf(provider)))

    @AfterTest
    fun cleanUp() {
        engine.close()
        workspace.close()
    }

    private fun callsAfterWriting(path: String): Int {
        engine.update()
        val before = provider.calls
        workspace.write(path, "class Changed\n")
        engine.update()
        return provider.calls - before
    }

    @Test
    fun `an unchanged workspace does not ask the provider again`() {
        engine.update()
        engine.update()
        assertEquals(1, provider.calls)
    }

    @Test
    fun `a change to a source of the build itself does not ask the provider again`() {
        assertEquals(0, callsAfterWriting("app/src/main/kotlin/Main.kt"))
    }

    @Test
    fun `a change to a source of a nested build asks the provider again`() {
        assertEquals(1, callsAfterWriting("build-logic/src/main/kotlin/ConventionPlugin.kt"))
    }

    @Test
    fun `a change to a source of buildSrc asks the provider again`() {
        assertEquals(1, callsAfterWriting("buildSrc/src/main/kotlin/Helper.kt"))
    }
}
