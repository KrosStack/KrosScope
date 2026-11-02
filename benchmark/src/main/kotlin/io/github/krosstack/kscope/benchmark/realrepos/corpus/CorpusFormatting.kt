package io.github.krosstack.kscope.benchmark.realrepos.corpus

internal object CorpusFormatting {
    fun normalForm(source: String): String {
        if ('$' in source || '\\' in source || nestedBlockComment(source)) return source
        return PROTECTED_OR_SPACE.replace(source) { match ->
            if (match.value.first() == ' ' || match.value.first() == '\t') " " else match.value
        }
    }

    private fun nestedBlockComment(source: String): Boolean = PROTECTED_OR_SPACE.findAll(source).any { match ->
        match.value.startsWith("/*") && match.value.indexOf("/*", 2) >= 0
    }

    private val PROTECTED_OR_SPACE = Regex(
        """(?s)//[^\r\n]*|/\*.*?\*/|`[^`\r\n]*`|"{3}.*?"{3}|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[ \t]+""",
    )
}
