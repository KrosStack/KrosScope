package io.github.krosstack.kscope.analyzer.gradle.tooling

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolingApiFetcherTest {
    private val directory = Files.createTempDirectory("kscope-fetcher")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a build without a Gradle wrapper is rejected before any Gradle is started`() {
        val request = FetchRequest(directory, directory.resolve("plugin.jar"), directory.resolve("init.gradle"))
        val outcome = assertIs<FetchOutcome.Rejected>(ToolingApiFetcher().fetch(request))
        assertTrue("gradle-wrapper.properties" in outcome.message)
    }
}
