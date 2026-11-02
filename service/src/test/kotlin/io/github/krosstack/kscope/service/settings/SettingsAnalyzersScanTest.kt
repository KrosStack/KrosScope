package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A switched-off analyzer group leaves no unit and no relationship behind, and switching it on restores the index. */
class SettingsAnalyzersScanTest {
    private val recordUnit = "kscope.analyzers"

    private fun checkRoundTrip(kind: AnalyzerKind, sample: AnalyzerSample) {
        sample.scan()
        val first = sample.dump()
        val before = sample.producers()
        val owned = sample.idsOf(kind)

        sample.switchOff(kind)
        sample.scan()
        val after = sample.producers()

        val removed = before.intersect(owned)
        assertTrue(removed.isNotEmpty(), "the sample exercises ${kind.key}: $before")
        assertEquals(before - removed + recordUnit, after, kind.key)

        sample.withoutFile()
        sample.scan()
        assertEquals(first, sample.dump(), kind.key)
        assertEquals(before, sample.producers(), kind.key)
    }

    private fun roundTrip(kind: AnalyzerKind, sampleName: String) {
        AnalyzerSample(sampleName).use { checkRoundTrip(kind, it) }
    }

    @Test
    fun `the android resources off removes their units and relationships and on restores the index`() =
        roundTrip(AnalyzerKind.ANDROID_RESOURCES, "droid")

    @Test
    fun `aidl off removes its units and relationships and on restores the index`() =
        roundTrip(AnalyzerKind.AIDL, "aidl")

    @Test
    fun `schemas off removes their units and relationships and on restores the index`() =
        roundTrip(AnalyzerKind.SCHEMAS, "wire")

    @Test
    fun `native bindings off removes their units and relationships and on restores the index`() =
        roundTrip(AnalyzerKind.NATIVE_BINDINGS, "jni")

    @Test
    fun `compose resources off removes their units and relationships and on restores the index`() =
        roundTrip(AnalyzerKind.COMPOSE_RESOURCES, "cmp")

    @Test
    fun `web bundles off removes their unit and on restores the index`() = roundTrip(AnalyzerKind.WEB_BUNDLES, "web")

    @Test
    fun `git history off removes its unit and on restores the index`() {
        assumeTrue(TestGit.available(), "git is not available")
        AnalyzerSample("tests").use { sample ->
            val root = sample.sample.root
            TestGit.run(root, listOf("init", "-q"))
            TestGit.run(root, listOf("add", "-A"))
            val identity = listOf("-c", "user.name=dev", "-c", "user.email=dev@example.invalid")
            TestGit.run(root, identity + listOf("commit", "-q", "-m", "first"))
            checkRoundTrip(AnalyzerKind.GIT_HISTORY, sample)
        }
    }

    @Test
    fun `switching a kind off redoes exactly the affected units and verification matches a clean scan`() {
        AnalyzerSample("aidl").use { sample ->
            sample.scan()
            sample.switchOff(AnalyzerKind.AIDL)
            val narrowed = sample.scan(verify = true)

            assertEquals(true, narrowed.verification?.matches)
            assertTrue(narrowed.unitsRemoved > 0)

            sample.withoutFile()
            val restored = sample.scan(verify = true)
            assertEquals(true, restored.verification?.matches)
            assertTrue(restored.unitsProduced > 0)
            assertEquals(0, sample.scan().unitsProduced)
        }
    }

    @Test
    fun `a file that switches every group on equals no file`() {
        SettingsWorkspace().use { workspace ->
            workspace.open { it.scan() }
            val without = workspace.open { it.canonicalDump() }
            val everyGroupOn = AnalyzerKind.entries.joinToString("", "[analyzers]\n") { "${it.key} = true\n" }
            workspace.write("kscope.toml", everyGroupOn)
            val result = workspace.open { it.scan(rebuild = true) }
            val with = workspace.open { it.canonicalDump() }

            assertEquals(without, with)
            assertNotEquals(0, result.unitsProduced)
        }
    }
}
