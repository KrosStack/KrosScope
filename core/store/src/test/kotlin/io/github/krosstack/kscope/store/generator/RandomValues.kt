package io.github.krosstack.kscope.store.generator

import io.github.krosstack.kscope.model.RepoPath
import kotlin.random.Random

/** Reproducible values with awkward characters: quotes, SQL fragments, percent signs, non-ASCII and emoji. */
internal class RandomValues(val random: Random) {
    fun <E : Enum<E>> pick(entries: List<E>): E = entries.random(random)

    fun <T> maybe(value: () -> T): T? = if (random.nextBoolean()) value() else null

    fun <T> listOfUpTo(size: Int, value: () -> T): List<T> = List(random.nextInt(size + 1)) { value() }

    fun text(minLength: Int = 0, maxLength: Int = MAX_TEXT): String =
        List(random.nextInt(minLength, maxLength + 1)) { ALPHABET.random(random) }.joinToString("")

    fun name(): String = text(1)

    fun hex(): String = List(HASH_LENGTH) { HEX_DIGITS.random(random) }.joinToString("")

    fun line(): Int = random.nextInt(1, MAX_LINE)

    fun repoPath(): RepoPath {
        val segments = List(random.nextInt(0, MAX_SEGMENTS)) { SEGMENTS.random(random) }
        return RepoPath(segments.joinToString("/"))
    }

    private companion object {
        const val MAX_TEXT = 12
        const val HASH_LENGTH = 64
        const val MAX_SEGMENTS = 4
        const val MAX_LINE = 500
        const val HEX_DIGITS = "0123456789abcdef"

        val SEGMENTS = listOf("src", "main", "a b", "x'y", "é", "日本", "😀", "50%", "a_b")

        val ALPHABET = listOf(
            "a", "Z", "7", " ", "'", "\"", "%", "_", "\\", "/", ":", "é", "日", "😀", "\n", "\t", ";", "-- ", "`", "{",
        )
    }
}
