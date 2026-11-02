package io.github.krosstack.kscope.cli.skill

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.resolve

internal class SkillShowCommand(private val version: String) : CliktCommand(name = "show") {
    private val common by RootOptions(emptySet())

    override fun help(context: Context) = "Prints the instructions of the kscope skill.\n\n" +
        "Use it to read what the skill tells an agent, or to paste it into the instructions of an agent that " +
        "reads no skill folder."

    override fun helpEpilog(context: Context) = "Examples:\n  kscope skill show\n  kscope skill show > AGENTS.md"

    override fun run() {
        resolve(common)
        echo(SkillBundle(version).skill(), trailingNewline = false)
    }
}
