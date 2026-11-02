package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.spi.search.EnglishStopWords
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Locale

internal class PlainTextSearch(documents: Map<String, String>, private val removeEnglishStopWords: Boolean = false) :
    AutoCloseable {
    private val connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    init {
        connection.createStatement().use { statement ->
            statement.execute(
                "CREATE VIRTUAL TABLE files USING fts5(path UNINDEXED, content, tokenize = 'porter unicode61')",
            )
        }
        connection.prepareStatement("INSERT INTO files(path, content) VALUES (?, ?)").use { insert ->
            documents.toSortedMap().forEach { (path, content) ->
                insert.setString(1, path)
                insert.setString(2, if (removeEnglishStopWords) words(content).joinToString(" ") else content)
                insert.addBatch()
            }
            insert.executeBatch()
        }
    }

    fun search(query: String): List<String> {
        val terms = words(query).distinct()
        if (terms.isEmpty()) return emptyList()
        val expression = terms.joinToString(" OR ") { "\"$it\"" }
        val sql = "SELECT path FROM files WHERE files MATCH ? ORDER BY bm25(files, 0.0, 1.0), path"
        return connection.prepareStatement(sql).use { select ->
            select.setString(1, expression)
            select.executeQuery().use(::paths)
        }
    }

    private fun paths(rows: ResultSet): List<String> = buildList {
        while (rows.next()) add(rows.getString(1))
    }

    private fun words(text: String): List<String> {
        val tokens = WORDS.findAll(text).map { it.value.lowercase(Locale.ROOT) }.toList()
        return if (removeEnglishStopWords) EnglishStopWords.remove(tokens) else tokens
    }

    override fun close(): Unit = connection.close()

    private companion object {
        val WORDS = Regex("[\\p{L}\\p{N}]+")
    }
}
