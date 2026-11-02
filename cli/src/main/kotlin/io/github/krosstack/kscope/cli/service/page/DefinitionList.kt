package io.github.krosstack.kscope.cli.service.page

internal object DefinitionList {
    fun html(rows: List<Pair<String, String>>): String = buildString {
        appendLine("<dl>")
        rows.forEach { (name, value) ->
            appendLine("<dt>${HtmlEscape.text(name)}</dt><dd>${HtmlEscape.text(value)}</dd>")
        }
        appendLine("</dl>")
    }
}
