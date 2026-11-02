package io.github.krosstack.kscope.benchmark

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalizationOptionsTest {
    @Test
    fun `held out inputs default to the frozen reference statements and accept explicit paths`() {
        val args = listOf("localization", "--mode", "default", "--out", "output", "--gold", "gold")

        assertEquals(Path.of("gold/heldout/statements.json").toAbsolutePath(), LocalizationOptions.parse(args)?.heldOut)
        val explicit = LocalizationOptions.parse(args + listOf("--held-out", "paraphrases"))
        assertEquals(Path.of("paraphrases"), explicit?.heldOut)
        assertNull(LocalizationOptions.parse(args + "--original-only")?.heldOut)
    }

    @Test
    fun `ambiguous unsupported and incomplete localization options are rejected`() {
        val args = listOf("localization", "--mode", "default", "--out", "output")
        val invalid = listOf(
            args + listOf("--held-out", "paraphrases", "--original-only"),
            args + listOf("--original-only", "--original-only"),
            args + listOf("--out", "another"),
            args + listOf("--unknown", "value"),
            args + "--held-out",
            listOf("localization", "--mode", "trusted", "--out", "output"),
        )

        invalid.forEach { assertNull(LocalizationOptions.parse(it), it.joinToString(" ")) }
    }
}
