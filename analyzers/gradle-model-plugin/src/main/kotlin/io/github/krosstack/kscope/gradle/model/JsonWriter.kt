package io.github.krosstack.kscope.gradle.model

/** Writes maps, lists, strings, numbers, booleans and null as compact JSON without any library. */
internal object JsonWriter {
    private const val HEX_RADIX = 16
    private const val UNICODE_DIGITS = 4

    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean, is Number -> out.append(value.toString())
            is Map<*, *> -> appendMap(out, value)
            is Iterable<*> -> appendList(out, value)
            is Array<*> -> appendList(out, value.asList())
            else -> appendString(out, value.toString())
        }
    }

    private fun appendMap(out: StringBuilder, map: Map<*, *>) {
        out.append('{')
        map.entries.forEachIndexed { index, entry ->
            if (index > 0) out.append(',')
            appendString(out, entry.key.toString())
            out.append(':')
            append(out, entry.value)
        }
        out.append('}')
    }

    private fun appendList(out: StringBuilder, items: Iterable<*>) {
        out.append('[')
        items.forEachIndexed { index, item ->
            if (index > 0) out.append(',')
            append(out, item)
        }
        out.append(']')
    }

    private fun appendString(out: StringBuilder, text: String) {
        out.append('"')
        text.forEach { char -> out.append(escaped(char)) }
        out.append('"')
    }

    private fun escaped(char: Char): String = when {
        char == '"' -> "\\\""
        char == '\\' -> "\\\\"
        char == '\n' -> "\\n"
        char == '\r' -> "\\r"
        char == '\t' -> "\\t"
        char < ' ' -> "\\u" + char.code.toString(HEX_RADIX).padStart(UNICODE_DIGITS, '0')
        else -> char.toString()
    }
}
