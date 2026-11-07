package io.github.krosstack.kscope.semantic.chunk

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import java.security.MessageDigest
import java.util.HexFormat

internal class ChunkText(private val cap: (String, Int) -> String) {
    fun capped(text: String): String {
        val points = text.codePoints().iterator()
        var bytes = 0
        var end = 0
        while (points.hasNext()) {
            val point = points.nextInt()
            val size = String(Character.toChars(point)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > MAX_BYTES) break
            bytes += size
            end += Character.charCount(point)
        }
        return cap(text.substring(0, end), MAX_TOKENS)
    }

    fun small(text: String): Boolean = cap(text, SMALL_TOKENS) == text

    fun chunk(kind: ChunkKind, text: String, nodes: List<NodeRef>, artifact: ArtifactRef): Chunk {
        val capped = capped(text)
        val key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(capped.toByteArray()))
        return Chunk(key, kind, capped, nodes, artifact)
    }

    fun outline(header: String, members: List<Symbol>): String {
        val lines = mutableListOf(header)
        for ((index, member) in members.withIndex()) {
            val omitted = members.size - index - 1
            val candidate = lines + (member.signature ?: member.name) +
                listOfNotNull("$omitted more members".takeIf { omitted > 0 })
            val text = candidate.joinToString("\n")
            if (capped(text) != text) break
            lines += member.signature ?: member.name
        }
        val omitted = members.size - lines.size + 1
        if (omitted == 0) return capped(lines.joinToString("\n"))
        val closing = "\n$omitted more members"
        var prefix = capped(lines.joinToString("\n"))
        while (capped(prefix + closing) != prefix + closing) {
            prefix = prefix.dropLast(1).dropLastWhile { it.isHighSurrogate() }
        }
        return prefix + closing
    }

    fun declaration(symbol: Symbol, lines: List<String>): String {
        val range = checkNotNull(symbol.declaration).range
        val body = if (range.startLine in 1..lines.size && range.endLine in range.startLine..lines.size) {
            val selected = lines.subList(range.startLine - 1, range.endLine).toMutableList()
            selected[selected.lastIndex] = selected.last().take((range.endColumn - 1).coerceAtLeast(0))
            selected[0] = selected.first().drop((range.startColumn - 1).coerceAtLeast(0))
            selected.joinToString("\n")
        } else {
            ""
        }
        return listOf(header(symbol), body).joinToString("\n")
    }

    fun header(symbol: Symbol): String = listOfNotNull(
        symbol.declaration?.artifact?.path?.value,
        symbol.signature ?: symbol.name,
        symbol.documentation,
    ).joinToString("\n")

    private companion object {
        const val MAX_BYTES = 32 * 1024
        const val MAX_TOKENS = 512
        const val SMALL_TOKENS = 63
    }
}
