package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.context.BlockMeasureKey
import io.github.krosstack.kscope.context.TokenCountKey
import io.github.krosstack.kscope.context.TokenCountStore
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.TokenCount
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The token counts of rendered fragments and the measures of fragment blocks, kept in one file of the index directory
 * so that the next run does not count them again. It is derived data: a file that is missing, unreadable or written by
 * another version of the format is treated as empty and replaced by the next flush, and deleting it costs nothing but
 * time. It is not part of the index, so `scan --verify` never sees it. A scan prunes the entries whose file content no
 * longer exists; the counts of whole packages belong to no file, so it keeps the newest few hundred of them.
 *
 * A measure is stored like a count, as an integer under a key of its own kind, so a file written before measures were
 * kept, or before the measures of blocks that come from the graph were kept, is read as it is. A block that is a slice
 * of a file and one that comes from the graph have keys of different kinds, with different numbers of fields. A
 * measure that an integer cannot hold, and a block whose key holds the separator of the fields, are not remembered.
 */
internal class TokenCountFile(file: Path) : TokenCountStore {
    private val disk = TokenCountDisk(file)
    private val entries: MutableMap<String, Int> by lazy { disk.read() }
    private var changed = false

    @Synchronized
    override fun recall(key: TokenCountKey): TokenCount? = entries[EntryKeys.of(key)]?.let(::TokenCount)

    @Synchronized
    override fun remember(key: TokenCountKey, count: TokenCount) = keep(EntryKeys.of(key), count.value)

    @Synchronized
    override fun recallMeasure(key: BlockMeasureKey): Long? = EntryKeys.of(key)?.let { entries[it] }?.toLong()

    @Synchronized
    override fun rememberMeasure(key: BlockMeasureKey, measure: Long) {
        val entry = EntryKeys.of(key)
        if (entry != null && measure in 0L..MAX_MEASURE) keep(entry, measure.toInt())
    }

    /** Writes the entries when this run added any; a file that cannot be written leaves the cache as it was. */
    @Synchronized
    fun flush() {
        if (changed && disk.write(entries)) changed = false
    }

    /** Drops the entries of every file content that is not in [liveContent]. */
    @Synchronized
    fun prune(liveContent: Set<ContentHash>) {
        val live = liveContent.map { it.value }.toSet()
        val dropped = entries.keys.retainAll { EntryKeys.isPackage(it) || EntryKeys.contentOf(it) in live }
        val surplus = entries.keys.filter(EntryKeys::isPackage).dropLast(PACKAGES_KEPT).toSet()
        if (dropped || surplus.isNotEmpty()) changed = true
        entries.keys.removeAll(surplus)
        flush()
    }

    /** Forgets every entry. A file that cannot be deleted stays; its entries still fit the content they name. */
    @Synchronized
    fun discard() {
        entries.clear()
        changed = false
        disk.delete()
    }

    @Synchronized
    fun reload() {
        entries.clear()
        entries.putAll(disk.read())
        changed = false
    }

    private fun keep(entry: String, value: Int) {
        if (entries.put(entry, value) != value) changed = true
    }

    companion object {
        const val FILE_NAME = "token-counts.bin"
        private const val PACKAGES_KEPT = 512
        private const val MAX_MEASURE = Int.MAX_VALUE.toLong()
    }
}

private class TokenCountDisk(private val file: Path) {
    fun read(): MutableMap<String, Int> = try {
        Files.newInputStream(file).buffered().use { TokenCountFormat.read(DataInputStream(it)) }
    } catch (_: IOException) {
        mutableMapOf()
    }

    fun write(entries: Map<String, Int>): Boolean = try {
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.newOutputStream(temporary).buffered().use { TokenCountFormat.write(DataOutputStream(it), entries) }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        true
    } catch (_: IOException) {
        false
    }

    fun delete(): Boolean = try {
        Files.deleteIfExists(file)
    } catch (_: IOException) {
        false
    }
}

/** The keys of the entries: the fields of a count or of a block measure joined by a separator. */
private object EntryKeys {
    private const val SEPARATOR = '|'
    private const val PACKAGE = "PACKAGE"
    private const val BLOCK = "BLOCK"
    private const val GRAPH_BLOCK = "GRAPH_BLOCK"
    private const val NO_RANGE = "-"
    private const val RENDERING_FIELD = 2

    fun of(key: TokenCountKey): String {
        val range = key.range?.let(::rangeOf) ?: NO_RANGE
        val rendering = key.rendering?.name ?: PACKAGE
        return joined(listOf(key.contentHash.value, range, rendering, key.estimator.value, key.textDigest))
    }

    fun of(key: BlockMeasureKey): String? {
        val fields = listOfNotNull(
            key.contentHash.value,
            key.range?.let(::rangeOf) ?: NO_RANGE,
            key.rendering.name,
            key.estimator.value,
            key.layoutDigest,
            key.textDigest,
            if (key.textDigest == null) BLOCK else GRAPH_BLOCK,
        )
        return if (fields.any { SEPARATOR in it }) null else joined(fields)
    }

    fun isPackage(entry: String): Boolean = entry.split(SEPARATOR).getOrNull(RENDERING_FIELD) == PACKAGE

    fun contentOf(entry: String): String = entry.substringBefore(SEPARATOR)

    private fun rangeOf(range: TextRange) =
        "${range.startLine}:${range.startColumn}-${range.endLine}:${range.endColumn}"

    private fun joined(fields: List<String>) = fields.joinToString(SEPARATOR.toString())
}

/** The layout of the file: a magic number, a format version, the number of entries, then each key with its value. */
private object TokenCountFormat {
    private const val MAGIC = 0x4B53544B43L
    private const val VERSION = 1

    fun read(input: DataInputStream): MutableMap<String, Int> {
        if (input.readLong() != MAGIC || input.readInt() != VERSION) return mutableMapOf()
        val entries = input.readInt()
        if (entries < 0) throw IOException("A negative entry count")
        val found = LinkedHashMap<String, Int>()
        repeat(entries) {
            val key = input.readUTF()
            val tokens = input.readInt()
            if (tokens < 0) throw IOException("A negative token count")
            found[key] = tokens
        }
        return found
    }

    fun write(output: DataOutputStream, counts: Map<String, Int>) {
        output.writeLong(MAGIC)
        output.writeInt(VERSION)
        output.writeInt(counts.size)
        counts.forEach { (key, tokens) ->
            output.writeUTF(key)
            output.writeInt(tokens)
        }
    }
}
