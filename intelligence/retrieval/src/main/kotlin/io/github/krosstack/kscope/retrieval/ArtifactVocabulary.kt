package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.spi.search.CodeTokenizer

/** Literal artifact and symbol-category names from the analyzer capability taxonomy. */
public object ArtifactVocabulary {
    private data class Entry(val category: SymbolCategory, val phrases: List<List<String>>)

    private val entries: List<Entry> = listOf(
        entry(
            SymbolCategory.RESOURCE,
            "resource", "resources",
            "android string", "android strings",
            "android color", "android colors",
            "android layout", "android layouts",
            "android menu", "android menus",
            "android navigation",
        ),
        entry(
            SymbolCategory.COMPONENT,
            "component", "components",
            "manifest", "manifests",
            "permission", "permissions",
            "android activity", "android activities",
            "android service", "android services",
            "android receiver", "android receivers",
            "android provider", "android providers",
        ),
        entry(SymbolCategory.CONTRACT, "contract", "contracts", "aidl", "jni"),
        entry(
            SymbolCategory.SCHEMA,
            "schema", "schemas",
            "protobuf", "proto", "protocol buffer", "protocol buffers",
            "SQLDelight", "sqldelight",
            "room",
        ),
        entry(
            SymbolCategory.BUILD,
            "build", "builds", "gradle",
            "catalog", "catalogs",
            "dependency", "dependencies",
            "plugin", "plugins",
            "source set", "source sets", "sourceset", "sourcesets",
            "variant", "variants",
            "flavor", "flavors",
        ),
        entry(SymbolCategory.TYPE, "type", "types"),
        entry(SymbolCategory.CALLABLE, "callable", "callables"),
        entry(SymbolCategory.VARIABLE, "variable", "variables"),
        entry(SymbolCategory.PACKAGE, "package", "packages"),
    )

    public fun hints(text: String): List<SymbolCategory> {
        val parts = CodeTokenizer.parts(text)
        if (parts.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { entry -> entry.phrases.any { phrase -> parts.containsPhrase(phrase) } }
            .map { it.category }
            .distinct()
            .sortedBy { it.name }
            .toList()
    }

    private fun entry(category: SymbolCategory, vararg phrases: String): Entry = Entry(
        category = category,
        phrases = phrases.map(CodeTokenizer::parts).distinct(),
    )

    private fun List<String>.containsPhrase(phrase: List<String>): Boolean {
        if (phrase.isEmpty() || phrase.size > size) return false
        return (0..size - phrase.size).any { start ->
            phrase.indices.all { offset -> this[start + offset] == phrase[offset] }
        }
    }
}
