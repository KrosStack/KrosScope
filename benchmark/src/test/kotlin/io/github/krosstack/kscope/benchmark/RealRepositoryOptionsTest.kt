package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RealRepositoryOptionsTest {
    @Test
    fun `prepare accepts repository configuration with the declared maximum`() {
        val options = parseRealRepositoryOptions(
            listOf("real-repos-prepare", "--repositories", "inputs.json", "--out", "manifest.json"),
        )
        assertIs<RealRepositoryOptions.Prepare>(options)
        assertEquals(Path.of("inputs.json"), options.repositories)
        assertEquals(Path.of("manifest.json"), options.output)
        assertEquals(60, options.limit)
    }

    @Test
    fun `run requires an explicit chronological split and freeze identity`() {
        val identity = "a".repeat(64)
        val options = parseRealRepositoryOptions(
            listOf(
                "real-repos-run", "--manifest", "manifest.json", "--out", "measurement",
                "--split", "test", "--checkpoint-key", identity,
            ),
        )
        assertIs<RealRepositoryOptions.Run>(options)
        assertEquals(RealSplit.TEST, options.split)
        assertEquals(identity, options.identity)
        assertEquals(Path.of("manifest.json"), options.manifest)
    }

    @Test
    fun `run accepts an optional repository filter and rejects unsafe ones`() {
        val base = listOf(
            "real-repos-run", "--manifest", "manifest.json", "--out", "measurement",
            "--split", "dev", "--checkpoint-key", "b".repeat(64),
        )
        val options = parseRealRepositoryOptions(base + listOf("--repository", "sample-app"))
        assertIs<RealRepositoryOptions.Run>(options)
        assertEquals("sample-app", options.repository)
        assertNull(assertIs<RealRepositoryOptions.Run>(parseRealRepositoryOptions(base)).repository)
        assertNull(parseRealRepositoryOptions(base + listOf("--repository", "../escape")))
        assertNull(parseRealRepositoryOptions(base + listOf("--repository", "a", "--repository", "b")))
    }

    @Test
    fun `run accepts a budget subset in any order and rejects unknown or repeated budgets`() {
        val base = listOf(
            "real-repos-run", "--manifest", "manifest.json", "--out", "measurement",
            "--split", "dev", "--checkpoint-key", "b".repeat(64),
        )
        val subset = parseRealRepositoryOptions(base + listOf("--budgets", "4000,2000"))
        val all = parseRealRepositoryOptions(base)
        assertEquals(listOf(2_000, 4_000), assertIs<RealRepositoryOptions.Run>(subset).budgets.map { it.value })
        assertEquals(
            listOf(2_000, 4_000, 8_000, 16_000),
            assertIs<RealRepositoryOptions.Run>(all).budgets.map { it.value },
        )
        listOf("3000", "2000,2000", "2000,", "", "2000;4000", "0", "-2000", "two").forEach { invalid ->
            assertNull(parseRealRepositoryOptions(base + listOf("--budgets", invalid)), invalid)
        }
        assertNull(parseRealRepositoryOptions(base + listOf("--budgets", "2000", "--budgets", "4000")))
    }

    @Test
    fun `report reads measured records without accepting a scoring split`() {
        val options = parseRealRepositoryOptions(
            listOf(
                "real-repos-report",
                "--manifest",
                "manifest.json",
                "--input",
                "measurement",
                "--out",
                "summary",
            ),
        )
        assertIs<RealRepositoryOptions.Report>(options)
        assertEquals(Path.of("measurement"), options.input)
    }

    @Test
    fun `ambiguous unknown incomplete and unsafe options are rejected`() {
        val prepare = listOf("real-repos-prepare", "--repositories", "inputs.json", "--out", "manifest.json")
        val run = listOf("real-repos-run", "--manifest", "manifest.json", "--out", "measurement")
        val invalid = listOf(
            emptyList(),
            prepare + listOf("--out", "other.json"),
            prepare + listOf("--unknown", "value"),
            prepare + listOf("--limit", "0"),
            prepare + listOf("--limit", "61"),
            prepare + listOf("--limit"),
            run + listOf("--split", "test"),
            run + listOf("--split", "all", "--checkpoint-key", "a".repeat(64)),
            run + listOf("--split", "test", "--checkpoint-key", "unfrozen"),
        )
        invalid.forEach { assertNull(parseRealRepositoryOptions(it), it.toString()) }
    }

    @Test
    fun `import takes a catalog, its freeze file, a checkout directory and defaults to the test split`() {
        val base = listOf(
            "real-repos-import", "--catalog", "tasks.json", "--freeze", "freeze.json",
            "--checkouts", "repos", "--out", "manifest.json",
        )
        val options = assertIs<RealRepositoryOptions.Import>(parseRealRepositoryOptions(base))
        assertEquals(Path.of("repos"), options.checkouts)
        assertEquals(RealSplit.TEST, options.split)
        val dev = assertIs<RealRepositoryOptions.Import>(parseRealRepositoryOptions(base + listOf("--split", "dev")))
        assertEquals(RealSplit.DEV, dev.split)
        assertNull(parseRealRepositoryOptions(base.dropLast(2)))
        assertNull(parseRealRepositoryOptions(base + listOf("--split", "all")))
    }
}
