package io.github.krosstack.kscope.analyzer.schema.lexing

/**
 * The index after the pair of punctuation that opens at [open]; the end of the tokens when it is never closed. Pairs
 * of the same kind nest.
 */
internal fun List<SourceToken>.afterBalanced(open: Int, opening: String, closing: String): Int {
    var depth = 0
    var index = open
    while (index < size) {
        if (this[index].isPunctuation(opening)) depth++
        if (this[index].isPunctuation(closing)) depth--
        index++
        if (depth == 0) return index
    }
    return size
}
