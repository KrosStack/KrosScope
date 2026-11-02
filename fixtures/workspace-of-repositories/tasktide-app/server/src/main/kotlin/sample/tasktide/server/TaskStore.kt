package sample.tasktide.server

class TaskStore {
    private val titles = mutableListOf<String>()

    fun add(title: String) {
        titles.add(0, title)
    }

    fun newestFirst(): List<String> = titles.toList()
}
