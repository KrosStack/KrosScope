package io.github.krosstack.kscope.model

private const val PART_COUNT = 5
private const val SCHEME_INDEX = 0
private const val ECOSYSTEM_INDEX = 1
private const val UNIT_INDEX = 2
private const val SOURCE_SET_INDEX = 3
private const val DESCRIPTOR_INDEX = 4
private const val QUOTE = '`'

private class MalformedSymbolId(val problem: String) : Exception(problem)

private fun malformed(problem: String): Nothing = throw MalformedSymbolId(problem)

internal object SymbolIdParser {
    private val partSeparator = Regex("(?<! ) (?! )")

    fun parse(text: String): ParsedSymbolId = try {
        ParsedSymbolId.Valid(identityOf(text))
    } catch (failure: MalformedSymbolId) {
        ParsedSymbolId.Invalid(text, failure.problem)
    }

    private fun identityOf(text: String): SymbolIdentity {
        val parts = text.split(partSeparator).map { it.replace("  ", " ") }
        if (parts.size != PART_COUNT) malformed("Expected $PART_COUNT parts but found ${parts.size}")
        if (parts[SCHEME_INDEX] != SYMBOL_ID_SCHEME) malformed("Unknown scheme '${parts[SCHEME_INDEX]}'")
        val ecosystem = SymbolEcosystem.entries.firstOrNull { it.token == parts[ECOSYSTEM_INDEX] }
            ?: malformed("Unknown ecosystem '${parts[ECOSYSTEM_INDEX]}'")
        val unit = wellFormedPart("unit", parts[UNIT_INDEX])
        val sourceSet = wellFormedPart("source set", parts[SOURCE_SET_INDEX])
        val descriptorText = wellFormedPart("descriptor", parts[DESCRIPTOR_INDEX])
        return SymbolIdentity(ecosystem, unit, sourceSet, SymbolDescriptor(DescriptorReader(descriptorText).read()))
    }

    private fun wellFormedPart(label: String, part: String): String {
        partProblem(label, part)?.let { malformed(it) }
        return part
    }
}

private class DescriptorReader(private val text: String) {
    private var position = 0

    fun read(): List<DescriptorSegment> {
        val segments = mutableListOf<DescriptorSegment>()
        while (position < text.length) segments += readSegment()
        return segments
    }

    private fun readSegment(): DescriptorSegment = when (text[position]) {
        '(' -> DescriptorSegment.Parameter(readEnclosedName('(', ')'))
        '[' -> DescriptorSegment.TypeParameter(readEnclosedName('[', ']'))
        else -> readNamedSegment()
    }

    private fun readNamedSegment(): DescriptorSegment {
        val name = readName()
        return when (val suffix = take()) {
            '/' -> DescriptorSegment.Namespace(name)
            '#' -> DescriptorSegment.Type(name)
            '.' -> DescriptorSegment.Term(name)
            ':' -> DescriptorSegment.Meta(name)
            '(' -> DescriptorSegment.Method(name, readDisambiguator())
            else -> malformed("Unexpected '$suffix' after '$name'")
        }
    }

    private fun readDisambiguator(): String {
        val end = text.indexOf(')', position)
        if (end < 0) malformed("Unterminated method disambiguator")
        val disambiguator = text.substring(position, end)
        if ('(' in disambiguator) malformed("Parentheses inside a method disambiguator")
        position = end + 1
        expect('.')
        return disambiguator
    }

    private fun readEnclosedName(open: Char, close: Char): String {
        expect(open)
        val name = readName()
        expect(close)
        return name
    }

    private fun readName(): String = if (peek() == QUOTE) readQuotedName() else readPlainName()

    private fun readPlainName(): String {
        val start = position
        while (position < text.length && text[position].isPlainNameCharacter()) position++
        if (start == position) malformed("Expected a name at offset $start")
        return text.substring(start, position)
    }

    private fun readQuotedName(): String {
        expect(QUOTE)
        val name = StringBuilder()
        while (true) {
            val character = take()
            if (character != QUOTE) {
                name.append(character)
            } else if (peek() == QUOTE) {
                name.append(take())
            } else {
                return name.toString()
            }
        }
    }

    private fun peek(): Char? = text.getOrNull(position)

    private fun take(): Char = (peek() ?: malformed("Unexpected end of descriptor")).also { position++ }

    private fun expect(expected: Char) {
        val actual = take()
        if (actual != expected) malformed("Expected '$expected' but found '$actual'")
    }
}
