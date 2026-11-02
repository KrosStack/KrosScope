package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextRole

internal object TextEdgeCases {
    private const val TASK = "change the rounding of the doubled values"
    private const val RELEVANCE_STEP = 0.05
    private const val BYTE_ORDER_MARK = 0xFEFF
    private const val PLAIN = "fun first(): Int = 1\nfun second(): Int = 2\n"

    private val scenarios = listOf(
        Scenario(
            "crlf line ends",
            path = "source/Crlf",
            text = "fun a(): Int = 1\r\nfun b(): Int = 2\r\n\r\nfun c() = 3\r\n",
        ),
        Scenario("no final newline", path = "source/Open", text = "fun first(): Int = 1\nfun last(): Int = 2"),
        Scenario("only newlines", path = "source/Blank", text = "\n\n\n"),
        Scenario(
            "leading blank lines and a trailing carriage return",
            path = "source/Lead",
            text = "\n\n  fun a() = 1\r",
        ),
        Scenario("binary content", path = "source/Binary", text = "fun a() = 1${Char(0)}"),
        Scenario(
            "backtick runs",
            path = "docs/Notes.md",
            text = "# Notes\n```kotlin\nfun a() = 1\n```\n````\ninner ``` fence\n````\n``````\nends with ticks ```",
            category = ArtifactCategory.DOCUMENTATION,
        ),
        Scenario("lone high surrogate in the path", path = "source/Broken\uD800Name", text = PLAIN),
        Scenario("lone low surrogate in the path", path = "source/\uDC00Broken", text = PLAIN),
        Scenario("line breaks in the path", path = "source/Line\nBreak\rName", text = PLAIN),
        Scenario("blank task", task = "  \n\t ", path = "source/Quiet", text = PLAIN),
        Scenario("lone surrogate in the task", task = "fix the \uD800 handling", path = "source/Task", text = PLAIN),
        Scenario(
            "task with blank lines and backticks",
            task = "explain ```code```\n\nthen `more` `\n\n",
            path = "source/Ticks",
            text = PLAIN,
        ),
        Scenario("backtick task", task = "`", path = "source/Tick", text = PLAIN),
        Scenario("slash task", task = "//", path = "source/Slash", text = PLAIN),
        Scenario("apostrophe task", task = "'s", path = "source/Apostrophe", text = PLAIN),
        Scenario("combining task", task = "e${Char(0x0301)}${Char(0x200D)}", path = "source/Combining", text = PLAIN),
        Scenario(
            "task of unicode spaces",
            task = "${Char(0x00A0)}${Char(0x2028)}",
            path = "source/Spaces",
            text = PLAIN,
        ),
        Scenario(
            "non ascii text",
            path = "source/Unicode",
            text = "${Char(BYTE_ORDER_MARK)}fun greeting() = \"héllo wörld α🙂漢字\"\n// 𝔘𝔫\nfun tail() = 1\n",
        ),
        Scenario("non ascii task", task = "αβγ 🙂 漢字", path = "source/Greek", text = PLAIN),
    )

    fun all(): List<EstimateCase> = scenarios.flatMap(::build)

    private fun build(scenario: Scenario): List<EstimateCase> {
        val workspace = EstimateWorkspace()
        val file = workspace.file(scenario.path, scenario.text, scenario.category)
        val symbols = workspace.declareLines(file, scenario.text)
        val whole = listOf(ContextCandidate(NodeRef.OfArtifact(file.ref), 1.0, ContextRole.EDIT))
        val parts = symbols.mapIndexed { index, symbol ->
            ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0 - index * RELEVANCE_STEP)
        }
        return listOf(
            EstimateCase("${scenario.name} as a file", workspace.fixture(), ContextInput(scenario.task, whole)),
            EstimateCase("${scenario.name} as declarations", workspace.fixture(), ContextInput(scenario.task, parts)),
        )
    }

    private class Scenario(
        val name: String,
        val task: String = TASK,
        val path: String,
        val text: String,
        val category: ArtifactCategory = ArtifactCategory.SOURCE,
    )
}
