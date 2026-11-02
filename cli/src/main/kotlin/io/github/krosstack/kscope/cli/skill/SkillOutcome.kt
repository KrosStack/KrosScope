package io.github.krosstack.kscope.cli.skill

import java.nio.file.Path

/** What installing or removing the skill did to one skill directory. */
internal sealed interface SkillOutcome {
    val directory: Path

    data class Installed(override val directory: Path) : SkillOutcome

    data class Updated(override val directory: Path) : SkillOutcome

    data class Removed(override val directory: Path) : SkillOutcome

    data class Absent(override val directory: Path) : SkillOutcome

    data class Foreign(override val directory: Path) : SkillOutcome

    data class Failed(override val directory: Path, val reason: String) : SkillOutcome
}
