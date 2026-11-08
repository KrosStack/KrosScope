package sample.sqldshared.db

class MediaRepository(private val database: MediaDatabase) {
    fun lastAlbumId(): Long = database.albumQueries.lastInsertRowId().executeAsOne()

    fun lastTrackId(): Long = database.trackQueries.lastInsertRowId().executeAsOne()

    fun albumSummaries(): List<Summary> = database.albumQueries.summary().executeAsList()

    fun trackSummaries(): List<Summary> = database.trackQueries.summary().executeAsList()

    fun total(summary: Summary): Long = summary.total

    fun albumTitleCodepoints(): List<TitleCodepoints> = database.albumQueries.titleCodepoints().executeAsList()
}
