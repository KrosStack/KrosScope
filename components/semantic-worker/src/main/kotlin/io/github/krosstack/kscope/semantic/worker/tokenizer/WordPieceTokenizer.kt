package io.github.krosstack.kscope.semantic.worker.tokenizer

internal class WordPieceTokenizer(private val file: TokenizerFile, private val maximum: Int) {
    private val normalizer = BertNormalizer(file)
    private val specials = Regex(file.special.joinToString("|", "(", ")", transform = Regex::escape))
    val pad: Long get() = file.pad

    fun encode(text: String, limit: Int = maximum): Encoding {
        require(limit >= SPECIAL_COUNT)
        val tokens = ArrayList<Long>()
        var start = 0
        specials.findAll(text).forEach { match ->
            tokens.addAll(ordinary(text.substring(start, match.range.first)))
            tokens.add(file.vocabulary.getValue(match.value))
            start = match.range.last + 1
        }
        tokens.addAll(ordinary(text.substring(start)))
        return Encoding((listOf(file.cls) + tokens.take(limit - SPECIAL_COUNT) + file.sep).toLongArray())
    }

    fun cap(text: String, maxTokens: Int): String {
        require(maxTokens >= SPECIAL_COUNT)
        val limit = minOf(maxTokens, maximum)
        if (encode(text, Int.MAX_VALUE).ids.size <= limit) return text
        val boundaries = listOf(0) + boundaries(text)
        val first = boundaries.getOrNull(1) ?: 0
        return if (encode(text.substring(0, first), Int.MAX_VALUE).ids.size > limit) {
            (text.codePointCount(0, first) - 1 downTo 0).asSequence()
                .map { text.substring(0, text.offsetByCodePoints(0, it)) }
                .first { encode(it, Int.MAX_VALUE).ids.size <= limit }
        } else {
            var low = 0
            var high = boundaries.lastIndex
            while (low < high) {
                val middle = low + (high - low + 1) / 2
                val prefix = text.substring(0, boundaries[middle])
                if (encode(prefix, Int.MAX_VALUE).ids.size <= limit) low = middle else high = middle - 1
            }
            text.substring(0, boundaries[low])
        }
    }

    private fun boundaries(text: String): List<Int> = buildList {
        var start = 0
        specials.findAll(text).forEach { match ->
            addAll(ordinaryEnds(text.substring(start, match.range.first)).map { start + it })
            add(match.range.last + 1)
            start = match.range.last + 1
        }
        addAll(ordinaryEnds(text.substring(start)).map { start + it })
    }

    private fun ordinaryEnds(text: String): List<Int> {
        val originalIndices = ArrayList<Int>()
        val normalized = normalizer.normalize(text, originalIndices)
        var end = 0
        return BertPreTokenizer.split(normalized).map { word ->
            end = normalized.indexOf(word, end) + word.length
            text.offsetByCodePoints(originalIndices[end - 1], 1)
        }
    }

    private fun ordinary(text: String): List<Long> = BertPreTokenizer.split(
        normalizer.normalize(text),
    ).flatMap(::pieces)

    private fun pieces(word: String): List<Long> {
        if (word.codePointCount(0, word.length) > file.maxWord) return listOf(file.unknown)
        return wordPieces(word) ?: listOf(file.unknown)
    }

    private fun wordPieces(word: String): List<Long>? {
        val result = ArrayList<Long>()
        var start = 0
        while (start < word.length) {
            val end = word.length.downTo(start + 1).firstOrNull { end ->
                file.vocabulary.containsKey(piece(word, start, end))
            } ?: return null
            result.add(file.vocabulary.getValue(piece(word, start, end)))
            start = end
        }
        return result
    }

    private fun piece(word: String, start: Int, end: Int): String =
        (if (start == 0) "" else file.continuation) + word.substring(start, end)

    private companion object {
        const val SPECIAL_COUNT = 2
    }
}
