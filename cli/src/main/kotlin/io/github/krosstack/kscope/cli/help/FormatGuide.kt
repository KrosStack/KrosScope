package io.github.krosstack.kscope.cli.help

/** What the help of `--format` says for a command: the values it advertises, and what each of them gives. */
internal enum class FormatGuide(val choices: String, val help: String) {
    EVERYTHING(
        "(text|json|markdown|dot|mermaid)",
        "How to print results: text for people, json for programs, markdown for plain text to paste into a " +
            "prompt, dot for Graphviz (graph and diagram only), mermaid (diagram only). Defaults to text.",
    ),
    TEXT_OR_JSON("(text|json)", "How to print the result: text for people, json for programs. Defaults to text."),
    PLAIN_TEXT_TOO(
        "(text|json|markdown)",
        "How to print the result: text for people, json for programs, markdown for plain text to paste into a " +
            "prompt. Defaults to text.",
    ),
    CONTEXT(
        "(text|json|markdown)",
        "How to print the context: text summarises what was chosen, markdown is the context itself, ready to " +
            "paste into a prompt, json has the fragments with their evidence. Defaults to text.",
    ),
    GRAPH(
        "(text|json|dot)",
        "How to print the graph: text for people, dot for Graphviz, json for programs, with the evidence of " +
            "every relationship. Defaults to text.",
    ),
    DIAGRAM(
        "(mermaid|dot|json)",
        "How to print the diagram: mermaid for Markdown pages and Mermaid tools, dot for Graphviz, json for " +
            "programs. Defaults to mermaid.",
    ),
    REPORT(
        "(text|json|markdown)",
        "How to print the result: text for people, json for programs, markdown for the written report. " +
            "Defaults to text.",
    ),
}
