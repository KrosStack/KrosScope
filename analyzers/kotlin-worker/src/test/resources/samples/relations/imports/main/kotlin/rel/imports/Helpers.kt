package rel.imports.helpers

fun help(): Int = 1

fun help(text: String): Int = text.length

class Tool

object Constants {
    const val SIZE: Int = 3

    fun make(): Tool = Tool()
}
