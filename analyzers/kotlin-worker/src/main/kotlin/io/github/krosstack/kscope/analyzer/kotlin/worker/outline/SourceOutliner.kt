package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.spi.worker.ScriptOutline
import org.jetbrains.kotlin.psi.KtFile

/**
 * Reads the Kotlin sources of build logic. The sources of one call are the source set whose functions a plugin class
 * can have inlined, so they are given together; each source's outline lists its plugin classes and no calls.
 */
internal class SourceOutliner(sources: List<KtFile>) {
    private val classes = PluginClassOutliner(FunctionIndex(sources))

    fun outline(file: KtFile): ScriptOutline = ScriptOutline(emptyList(), classes.outline(file))
}
