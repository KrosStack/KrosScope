package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.RemoveResult

internal class ComponentsRemoveCommand(private val directory: ComponentDirectory) : CliktCommand(name = "remove") {
    private val name by argument("NAME")
    override fun help(context: Context) = "Removes only directories marked for the named component."

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope components remove semantic",
    ).joinToString("\n")

    override fun run() = guarded {
        val result = directory.remove(name)
        echo(ComponentsText.render(name, result), err = result is RemoveResult.Refused)
        if (result is RemoveResult.Refused) throw ProgramResult(1)
    }
}
