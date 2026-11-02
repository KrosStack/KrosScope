package io.github.krosstack.kscope.cli.skill

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.krosstack.kscope.cli.ExitCodes

/** Prints one line for each skill directory and ends with exit code 1 when a directory was refused or failed. */
internal fun CliktCommand.report(outcomes: List<Pair<SkillTarget, SkillOutcome>>) {
    outcomes.forEach { (target, outcome) -> echo(line(target, outcome), err = isProblem(outcome)) }
    if (outcomes.any { isProblem(it.second) }) throw ProgramResult(ExitCodes.FAILURE)
}

private fun isProblem(outcome: SkillOutcome): Boolean =
    outcome is SkillOutcome.Foreign || outcome is SkillOutcome.Failed

private fun line(target: SkillTarget, outcome: SkillOutcome): String = when (outcome) {
    is SkillOutcome.Installed -> "Installed the kscope skill for ${target.agents} in ${outcome.directory}."
    is SkillOutcome.Updated -> "Updated the kscope skill for ${target.agents} in ${outcome.directory}."
    is SkillOutcome.Removed -> "Removed the kscope skill from ${outcome.directory}."
    is SkillOutcome.Absent -> "No kscope skill in ${outcome.directory}."
    is SkillOutcome.Foreign -> "Left ${outcome.directory} alone: kscope did not install what it holds."
    is SkillOutcome.Failed -> "Could not change ${outcome.directory}: ${outcome.reason}"
}
