package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId

private const val NAMESPACE = "proto"
private val packageStatement = Regex("""(?:\A|[;{}])\s*(package\s+[A-Za-z_][\w.]*\s*;)""")

/**
 * The symbol of a proto file as a whole, named after the file and located at its `package` statement, or at its first
 * line when it has none. It lets a link end at the file that a generator reads, whatever the file declares.
 */
internal class ProtoFileSymbol(
    private val path: String,
    private val text: String,
    private val identity: IdentityFactory,
) {
    fun symbol(packageName: String): SchemaSymbol {
        val place = packageSpan() ?: firstLine()
        return SchemaSymbol(
            id = idOf(identity, path),
            kind = ProtoKinds.file,
            name = path.substringAfterLast('/'),
            qualifiedName = path,
            signature = if (packageName.isEmpty()) path.substringAfterLast('/') else "package $packageName",
            container = null,
            declaration = place,
            nameSpan = place,
            attributes = packageName.takeIf { it.isNotEmpty() }
                ?.let { mapOf(ProtoKinds.PACKAGE_ATTRIBUTE to it) }.orEmpty(),
        )
    }

    private fun packageSpan(): Span? =
        packageStatement.find(withoutOpaqueText())?.groups?.get(1)?.range?.let { Span(it.first, it.last + 1) }

    private fun firstLine(): Span = Span(0, text.lineSequence().first().length)

    private fun withoutOpaqueText(): String {
        val blanked = StringBuilder(text)
        var index = 0
        while (index < text.length) {
            val end = opaqueEnd(index)
            for (blank in index until end) if (text[blank] != '\n') blanked.setCharAt(blank, ' ')
            index = maxOf(end, index + 1)
        }
        return blanked.toString()
    }

    private fun opaqueEnd(at: Int): Int = when {
        text.startsWith("//", at) -> text.indexOf('\n', at).let { if (it < 0) text.length else it }
        text.startsWith("/*", at) -> text.indexOf("*/", at + 2).let { if (it < 0) text.length else it + 2 }
        text[at] == '"' || text[at] == '\'' -> endOfString(at)
        else -> at
    }

    private fun endOfString(open: Int): Int {
        val quote = text[open]
        var cursor = open + 1
        while (cursor < text.length && text[cursor] != quote && text[cursor] != '\n') {
            cursor += if (text[cursor] == '\\') 2 else 1
        }
        return minOf(cursor + 1, text.length)
    }

    companion object {
        /** The id of the symbol of the proto file at [path], in the module and source set that [identity] names. */
        fun idOf(identity: IdentityFactory, path: String): SymbolId =
            identity.idOf(SymbolDescriptor().namespace(NAMESPACE).meta(path))
    }
}
