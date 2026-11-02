package io.github.krosstack.kscope.cli.text.format

/** How the first line of a result reads: as plain news, as good news, as a warning or as a failure. */
internal enum class Tone { NEUTRAL, GOOD, WARNING, BAD }

/**
 * Builds the text of one command result: a conclusion, then sections of fields, tables and lists. A document made
 * with [shown] prints only the sections named there; the lines before the first section belong to the headline.
 */
internal class TextDocument(private val context: TextContext, shown: Set<String>? = null) {
    private val emphasis = context.emphasis
    private val lines = mutableListOf<String>()
    private val shows: (String) -> Boolean = { shown == null || it == Section.ANYTHING || it in shown }
    private var visible = shows(Section.HEADLINE)

    private fun add(line: String) {
        if (visible) lines += line
    }

    fun conclusion(text: String, tone: Tone = Tone.NEUTRAL): TextDocument = apply {
        add(
            when (tone) {
                Tone.NEUTRAL -> emphasis.bold(text)
                Tone.GOOD -> emphasis.good(text)
                Tone.WARNING -> emphasis.warning(text)
                Tone.BAD -> emphasis.bad(text)
            },
        )
    }

    fun line(text: String = ""): TextDocument = apply { add(text) }

    fun note(text: String): TextDocument = apply { add(emphasis.dim(text)) }

    /** Starts the section [name] with a heading when [title] is given; its lines print if the section is shown. */
    fun section(title: String?, name: String = Section.ANYTHING): TextDocument = apply {
        visible = shows(name)
        add("")
        title?.let { add(emphasis.bold(it)) }
    }

    fun fields(fields: List<Pair<String, String>>): TextDocument = apply {
        val width = fields.maxOfOrNull { it.first.length } ?: 0
        fields.forEach { (label, value) -> add("  ${label.padEnd(width)}  $value".trimEnd()) }
    }

    fun table(table: Table): TextDocument = apply {
        if (visible) lines += table.render(width = context.width, emphasis = emphasis)
    }

    fun items(items: List<String>, limit: Int = DEFAULT_LIMIT): TextDocument = apply {
        val room = context.width?.let { maxOf(it - ITEM_INDENT, Table.MINIMUM_PATH_WIDTH) }
        items.take(limit).forEach { add("  ${room?.let { width -> PathShortening.shorten(it, width) } ?: it}") }
        more(items.size - limit)
    }

    fun more(remaining: Int, what: String = "more (use --format json for the full list)"): TextDocument = apply {
        if (remaining > 0) add(emphasis.dim("  ${Format.count(remaining)} $what"))
    }

    fun hint(text: String): TextDocument = apply {
        add("")
        add(emphasis.dim(text))
    }

    override fun toString(): String = lines.joinToString(NEWLINE).trimStart(NEWLINE.single())

    companion object {
        const val DEFAULT_LIMIT = 10
        private const val ITEM_INDENT = 2
        private const val NEWLINE = "\n"
    }
}

/** The names of the sections of a ranked result, as `--sections` accepts them. */
internal object Section {
    const val ANYTHING = ""
    const val HEADLINE = "headline"
    const val QUERY = "query"
    const val TOP = "top"
    const val EDIT = "edit"
    const val READ = "read"
    const val DOCUMENTS = "documents"
    const val CUTOFF = "cutoff"
    const val TESTS = "tests"
    const val SCOPE = "scope"
    const val GAPS = "gaps"
    const val CONNECTEDNESS = "connectedness"
    const val CONTEXT = "context"
    const val NEXT = "next"
    const val DECLARATION = "declaration"
    const val DEPENDS_ON = "depends-on"
    const val REFERRED_BY = "referred-by"
    const val IMPACT = "impact"
    const val ALTERNATIVES = "alternatives"
    const val BEST = "best"
    const val MATCHES = "matches"
    const val FILES = "files"
    const val SYMBOLS = "symbols"
    const val SUPPORT = "support"
}
