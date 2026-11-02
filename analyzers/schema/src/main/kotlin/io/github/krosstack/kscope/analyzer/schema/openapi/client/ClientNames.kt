package io.github.krosstack.kscope.analyzer.schema.openapi.client

private val separators = Regex("[^A-Za-z0-9]+")

/** The names the OpenAPI Generator gives to classes and members: camel case, with what is not alphanumeric gone. */
internal object ClientNames {
    fun upperCamel(name: String): String = name.split(separators).filter { it.isNotEmpty() }
        .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        .ifEmpty { name }

    fun lowerCamel(name: String): String = upperCamel(name).replaceFirstChar(Char::lowercaseChar)
}
