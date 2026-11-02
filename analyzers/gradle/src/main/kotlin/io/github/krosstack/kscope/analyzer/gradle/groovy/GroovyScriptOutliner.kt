package io.github.krosstack.kscope.analyzer.gradle.groovy

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.spi.ScriptOutliner
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.SourceUnit

/**
 * Outlines Groovy build scripts from the syntax tree the Apache Groovy parser builds at the conversion phase. The
 * script is parsed and nothing else: it is never compiled to classes, executed or resolved.
 *
 * The outline has the shape of the Kotlin script outline. Command calls (`id 'x'`, `include ':a', ':b'`), named
 * arguments (`apply plugin: 'java'`) and closure bodies become calls, arguments and bodies; a script with syntax
 * errors is absent from the result.
 */
public class GroovyScriptOutliner : ScriptOutliner {
    override fun outline(scripts: List<Artifact>, files: WorkspaceFiles): Map<ArtifactRef, ScriptOutline> =
        scripts.mapNotNull { script ->
            outlineOf(files.read(script.ref).toString(Charsets.UTF_8))?.let { script.ref to it }
        }.toMap()

    internal fun outlineOf(text: String): ScriptOutline? {
        val statements = parse(text)?.statementBlock?.statements ?: return null
        return ScriptOutline(GroovyStatements(SourceText(text)).read(statements, nested = false).calls)
    }

    private fun parse(text: String) = try {
        SourceUnit.create(SCRIPT_NAME, text).also { unit ->
            unit.parse()
            unit.completePhase()
            unit.nextPhase()
            unit.convert()
        }.ast
    } catch (ignored: CompilationFailedException) {
        null
    }

    private companion object {
        const val SCRIPT_NAME = "build-script"
    }
}
