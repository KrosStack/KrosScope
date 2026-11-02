package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.Golden
import kotlin.test.AfterTest
import kotlin.test.Test

class ScriptOutlineGoldenTest {
    private val fixture = OutlineFixture()

    @AfterTest
    fun closeFixture() = fixture.close()

    @Test
    fun `a settings script matches the golden outline`() {
        Golden.assertMatches("outline-settings.jsonl", Golden.outline(fixture.ofSample("scripts/settings.gradle.kts")))
    }

    @Test
    fun `an application build script matches the golden outline`() {
        val outline = fixture.ofSample("scripts/app/build.gradle.kts")
        Golden.assertMatches("outline-app-build.jsonl", Golden.outline(outline))
    }

    @Test
    fun `a multiplatform library build script matches the golden outline`() {
        val outline = fixture.ofSample("scripts/lib/build.gradle.kts")
        Golden.assertMatches("outline-lib-build.jsonl", Golden.outline(outline))
    }
}
