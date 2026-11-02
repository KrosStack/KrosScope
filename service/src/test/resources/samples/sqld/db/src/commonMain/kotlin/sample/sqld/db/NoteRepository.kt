package sample.sqld.db

class NoteRepository(private val database: NotesDatabase) {
    private val queries = database.noteQueries

    fun all(): List<Note> = queries.selectAll().executeAsList()

    fun find(id: Long): Note? = queries.selectById(id).executeAsOneOrNull()

    fun titles(): List<String> = queries.titles(pinned = true).executeAsList()

    fun add(title: String, body: String?) {
        queries.insertNote(title, body)
    }

    fun clear() {
        queries.deleteAll()
    }

    fun summary(note: Note): String = note.title + note.body.orEmpty()
}
