package io.github.krosstack.kscope.benchmark.agent.verification

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TestResultsTest {
    @Test
    fun `junit result files are read into one outcome per test`(@TempDir workspace: Path) {
        val results = workspace.resolve("m/build/test-results/test").also { Files.createDirectories(it) }
        Files.writeString(
            results.resolve("TEST-p.NewTest.xml"),
            """<?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="p.NewTest" tests="3">
              <testcase name="works" classname="p.NewTest" time="0.1"/>
              <testcase name="broken" classname="p.NewTest"><failure message="x">trace</failure></testcase>
              <testcase name="later" classname="p.NewTest"><skipped/></testcase>
            </testsuite>
            """.trimIndent(),
        )
        Files.writeString(results.resolve("TEST-broken.xml"), "<testsuite><testcase")

        val outcomes = TestResults.read(workspace)

        assertEquals(
            mapOf(
                "p.NewTest#broken" to TestOutcome.FAILED,
                "p.NewTest#later" to TestOutcome.SKIPPED,
                "p.NewTest#works" to TestOutcome.PASSED,
            ),
            outcomes,
        )
    }

    @Test
    fun `a test with the same name in several variants fails when any variant fails`(@TempDir workspace: Path) {
        listOf("testDemoDebugUnitTest" to "", "testFullDebugUnitTest" to "<failure/>").forEach { (task, body) ->
            val directory = workspace.resolve("app/build/test-results/$task").also { Files.createDirectories(it) }
            Files.writeString(
                directory.resolve("TEST-p.Shared.xml"),
                "<testsuite><testcase name=\"shared\" classname=\"p.Shared\">$body</testcase></testsuite>",
            )
        }

        assertEquals(mapOf("p.Shared#shared" to TestOutcome.FAILED), TestResults.read(workspace))
    }
}
