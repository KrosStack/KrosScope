package io.github.krosstack.kscope.analyzer.jni.mangling

import io.github.krosstack.kscope.model.Symbol

private val annotations = Regex("@[\\w.]+(\\([^)]*\\))?\\s*")
private val kotlinModifiers = setOf("vararg", "noinline", "crossinline")
private val javaModifiers = setOf("final")

/**
 * The parameter types of an `external` or `native` declaration, read from the one-line signature of its symbol. They
 * are the types as written, such as `ByteArray`, `Array<String>` or `byte[]`; null when the signature is not
 * understood, or when an extension receiver adds a parameter that the signature does not show.
 */
internal object ExternalParameters {
    fun of(symbol: Symbol, name: String): List<String>? {
        val signature = symbol.signature
        val open = signature?.let { Regex("(?<!\\w)" + Regex.escape(name) + "\\(").find(it)?.range?.last }
        return if (signature == null || open == null) {
            null
        } else {
            parameters(signature, open, name, symbol.kind.id.startsWith("kotlin."))
        }
    }

    private fun parameters(signature: String, open: Int, name: String, isKotlin: Boolean): List<String>? {
        val hasReceiver = isKotlin && signature.substring(0, open - name.length).trimEnd().endsWith(".")
        val list = parenthesized(signature, open)
        val types = list?.let(::split)?.map { if (isKotlin) kotlinType(it) else javaType(it) }
        return types.takeUnless { hasReceiver }
    }

    private fun parenthesized(text: String, open: Int): String? {
        var depth = 0
        for (index in open until text.length) {
            if (text[index] == '(') depth++
            if (text[index] == ')' && --depth == 0) return text.substring(open + 1, index)
        }
        return null
    }

    private fun split(list: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        list.forEachIndexed { index, character ->
            if (character in "<([") depth++
            if (character in ">)]") depth--
            if (character == ',' && depth == 0) {
                parts += list.substring(start, index)
                start = index + 1
            }
        }
        return (parts + list.substring(start)).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun kotlinType(parameter: String): String {
        val text = parameter.replace(annotations, "").trim()
        val words = text.split(Regex("\\s+"))
        val isVararg = words.any { it == "vararg" }
        val declaration = words.filterNot { it in kotlinModifiers }.joinToString(" ")
        val type = declaration.substringAfter(':', declaration).substringBefore('=').trim()
        return if (isVararg) "$type..." else type
    }

    private fun javaType(parameter: String): String {
        val text = parameter.replace(annotations, "").trim()
        val words = text.split(Regex("\\s+")).filterNot { it in javaModifiers }
        return words.dropLast(1).joinToString(" ")
    }
}
