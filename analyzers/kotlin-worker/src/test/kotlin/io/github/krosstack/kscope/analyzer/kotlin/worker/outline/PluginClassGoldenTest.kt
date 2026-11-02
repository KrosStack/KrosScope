package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.Golden
import kotlin.test.AfterTest
import kotlin.test.Test

class PluginClassGoldenTest {
    private val fixture = OutlineFixture()

    @AfterTest
    fun closeFixture() = fixture.close()

    @Test
    fun `the plugin classes of the convention sample match the golden outline`() {
        Golden.assertMatches("outline-plugin-classes.jsonl", fixture.ofSources("plugins/convention").pluginClassLines())
    }
}
