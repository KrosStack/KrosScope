package io.github.krosstack.kscope.cli.freshness

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.obj
import io.github.krosstack.kscope.cli.CliSession
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.lookup.askedFrom
import io.github.krosstack.kscope.cli.text.FreshnessText
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.freshness.FreshnessInputs
import io.github.krosstack.kscope.service.freshness.FreshnessState
import io.github.krosstack.kscope.service.freshness.IndexFreshness
import io.github.krosstack.kscope.service.freshness.freshnessInputs

internal class Staleness(private val command: CliktCommand) {
    private var inputs: FreshnessInputs? = null

    fun read(scope: KrosScope, options: ResolvedOptions) {
        val session = command.currentContext.parent?.obj as? CliSession
        val watched = session?.execution?.watched ?: true
        inputs = if (watched) null else scope.freshnessInputs(options.askedFrom())
    }

    fun behind(): IndexFreshness? = inputs?.check()?.takeIf { it.state != FreshnessState.NOT_BEHIND }
}

internal fun CliktCommand.warnBehind(freshness: IndexFreshness?, format: OutputFormat) {
    val line = freshness?.let(FreshnessText::line)
    if (line != null && format != OutputFormat.JSON) echo(line, err = true)
}
