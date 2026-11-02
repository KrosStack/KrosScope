package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.spi.search.SearchHit
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope
import io.github.krosstack.kscope.spi.search.SearchTerm
import java.sql.Connection
import java.sql.DriverManager

internal fun ScanEngine.hits(scope: SearchScope, vararg words: String, limit: Int = 50): List<SearchHit> =
    searchIndex().search(SearchQuery(words.map { SearchTerm(it) }, scope, limit))

internal fun List<SearchHit>.paths(): List<String> = mapNotNull { (it.node as? NodeRef.OfArtifact)?.ref?.path?.value }

internal fun List<SearchHit>.symbolIds(): List<String> = mapNotNull { (it.node as? NodeRef.OfSymbol)?.id?.value }

/** A second connection to the index file, to look at it or to damage it while the engine has it open. */
internal fun TestWorkspace.indexConnection(): Connection =
    DriverManager.getConnection("jdbc:sqlite:${indexDirectory.resolve("index.db")}")

internal fun Connection.execute(sql: String) = createStatement().use { it.execute(sql) }

internal fun Connection.dataVersion(): Int = createStatement().use { statement ->
    statement.executeQuery("PRAGMA data_version").use { rows ->
        rows.next()
        rows.getInt(1)
    }
}
