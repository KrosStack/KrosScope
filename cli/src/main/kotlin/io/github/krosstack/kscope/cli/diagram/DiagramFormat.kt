package io.github.krosstack.kscope.cli.diagram

import com.github.ajalt.clikt.core.UsageError
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ServiceJson

internal enum class DiagramFormat(val output: OutputFormat) {
    MERMAID(OutputFormat.MERMAID),
    DOT(OutputFormat.DOT),
    JSON(OutputFormat.JSON),
    ;

    fun render(diagram: Diagram, level: ScanLevel?, indexLocation: String? = null): String = when (this) {
        MERMAID -> MermaidEmitter.render(diagram).trimEnd('\n')
        DOT -> DotEmitter.render(diagram).trimEnd('\n')
        JSON -> ServiceJson.encode(diagram, level, indexLocation)
    }

    companion object {
        fun of(format: OutputFormat): DiagramFormat = entries.firstOrNull { it.output == format }
            ?: throw UsageError(
                "--format ${format.name.lowercase()} is not available for diagram; use mermaid, dot or json.",
            )
    }
}
