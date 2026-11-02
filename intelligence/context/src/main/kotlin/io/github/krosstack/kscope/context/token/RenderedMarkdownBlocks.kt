package io.github.krosstack.kscope.context.token

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextRendering
import java.util.LinkedHashMap

internal class RenderedMarkdownBlocks(
    private val locations: WorkspaceLocations = WorkspaceLocations.SINGLE,
    private val maxCharacters: Int = MAX_CHARACTERS,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val rendered = LinkedHashMap<MarkdownRenderKey, String>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private var characters = 0

    fun fragment(fragment: ContextFragment): String {
        val key = MarkdownRenderKey(
            fragment.artifact,
            fragment.range,
            fragment.rendering,
            fragment.text,
            fragment.evidenceNote,
        )
        val cached = rendered[key]
        if (cached != null) return cached
        val text = ContextMarkdown.fragment(fragment, locations)
        val size = key.characters + text.length + ENTRY_ALLOWANCE
        if (size <= maxCharacters) {
            rendered[key] = text
            characters += size
            evict()
        }
        return text
    }

    private fun evict() {
        while (rendered.size > maxEntries || characters > maxCharacters) {
            val iterator = rendered.entries.iterator()
            val entry = iterator.next()
            characters -= entry.key.characters + entry.value.length + ENTRY_ALLOWANCE
            iterator.remove()
        }
    }

    private companion object {
        const val MAX_CHARACTERS = 4 * 1024 * 1024
        const val MAX_ENTRIES = 4096
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
        const val ENTRY_ALLOWANCE = 256
    }
}

private data class MarkdownRenderKey(
    val artifact: ArtifactRef,
    val range: TextRange?,
    val rendering: ContextRendering,
    val text: String,
    val evidenceNote: String?,
) {
    val characters: Int get() = artifact.repository.value.length + artifact.path.value.length +
        text.length + (evidenceNote?.length ?: 0)
}
