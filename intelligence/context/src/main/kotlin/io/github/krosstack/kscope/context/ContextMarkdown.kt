package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextPackage

public object ContextMarkdown {
    public fun render(context: ContextPackage): String = context.markdown

    public fun renderContents(
        task: String,
        fragments: List<ContextFragment>,
        unresolved: List<Relationship> = emptyList(),
        locations: WorkspaceLocations = WorkspaceLocations.SINGLE,
    ): String = blocks(task, fragments, unresolved, locations).joinToString("")

    public fun blocks(
        task: String,
        fragments: List<ContextFragment>,
        unresolved: List<Relationship> = emptyList(),
        locations: WorkspaceLocations = WorkspaceLocations.SINGLE,
    ): List<String> = blocks(task, fragments, unresolved) { fragment(it, locations) }

    internal fun blocks(
        task: String,
        fragments: List<ContextFragment>,
        unresolved: List<Relationship>,
        render: (ContextFragment) -> String,
    ): List<String> = blocks(task, fragments, unresolved.size, render)

    internal fun blocks(
        task: String,
        fragments: List<ContextFragment>,
        unresolvedCount: Int,
        render: (ContextFragment) -> String,
    ): List<String> = listOfNotNull(taskBlock(task)) + fragments.map(render) +
        listOfNotNull(unresolvedBlock(unresolvedCount))

    internal fun taskBlock(task: String): String? = if (task.isNotBlank()) "Task: ${task.trim()}\n\n" else null

    internal fun unresolvedBlock(count: Int): String? = if (count > 0) "Unresolved relationships: $count\n" else null

    internal fun fragment(
        fragment: ContextFragment,
        locations: WorkspaceLocations = WorkspaceLocations.SINGLE,
    ): String = buildString { appendFragment(fragment, locations) }

    internal fun printedPath(
        artifact: ArtifactRef,
        locations: WorkspaceLocations = WorkspaceLocations.SINGLE,
    ): String = locations.pathOf(artifact).replace('\n', ' ').replace('\r', ' ')

    private fun StringBuilder.appendFragment(fragment: ContextFragment, locations: WorkspaceLocations) {
        val longest = BACKTICKS.findAll(fragment.text).maxOfOrNull { it.value.length + 1 } ?: 0
        val fence = "`".repeat(maxOf(MIN_FENCE, longest))
        append(fence).append(' ').append(printedPath(fragment.artifact, locations))
        fragment.range?.let { append(':').append(it.startLine).append('-').append(it.endLine) }
        append(" [").append(fragment.rendering.name.lowercase()).append("]\n")
        append(fragment.text)
        if (!fragment.text.endsWith('\n')) append('\n')
        append(fence).append("\n")
        fragment.evidenceNote?.let { append(it).append('\n') }
        append('\n')
    }

    private const val MIN_FENCE = 3
    private val BACKTICKS = Regex("`+")
}
