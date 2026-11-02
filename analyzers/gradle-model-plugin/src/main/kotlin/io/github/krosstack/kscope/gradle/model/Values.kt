package io.github.krosstack.kscope.gradle.model

import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.io.File

/** Turns the loosely typed values read from plugin objects into plain lists, strings, numbers and paths. */
internal object Values {
    fun items(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is Provider<*> -> items(value.orNull)
        is Iterable<*> -> value.toList()
        is Array<*> -> value.toList()
        else -> listOf(value)
    }

    fun names(value: Any?): List<String> = items(value).map { (Reflect.get(it, "name") ?: it).toString() }.sorted()

    fun text(value: Any?): String? = plain(value)?.toString()

    fun plain(value: Any?): Any? = when (value) {
        null -> null
        is Provider<*> -> plain(value.orNull)
        is Iterable<*> -> value.map { plain(it) }
        is Array<*> -> value.map { plain(it) }
        is Number, is Boolean -> value
        else -> scalarText(value)
    }

    private fun scalarText(value: Any): String = when (value) {
        is FileSystemLocation -> slashPath(value.asFile)
        is File -> slashPath(value)
        is Enum<*> -> value.name
        else -> value.toString()
    }

    fun flatten(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is Iterable<*> -> value.flatMap { flatten(it) }
        else -> listOf(value)
    }

    fun slashPath(file: File): String = file.toPath().toAbsolutePath().normalize().toString().replace('\\', '/')
}
