package io.github.krosstack.kscope.benchmark.realrepos.corpus

internal object CorpusStatement {
    fun clean(message: String, paths: List<String>): String {
        val labels = RealSourceDetector().statementLabels(paths)
        val cleaned = labels.fold(message) { text, label ->
            text.replace(Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(label)}(?![\\p{L}\\p{N}_])"), " ")
        }
        return RealSourceDetector().withoutPaths(cleaned).replace(PR_NUMBER, " ")
            .replace(EMPTY_BRACKETS, " ").replace(SPACES, " ").trim()
    }

    private val PR_NUMBER = Regex("(?i)(?:\\(#\\d+\\)|\\b(?:PR|pull request)\\s*#?\\d+\\b|(?<!\\w)#\\d+\\b)")
    private val EMPTY_BRACKETS = Regex("[(`\\[]\\s*[)`\\]]")
    private val SPACES = Regex("\\s+")
}
