package io.github.krosstack.kscope.cli.skill

import java.nio.file.Path

/** A folder of skills that some coding agents read, and the folder the kscope skill takes inside it. */
internal data class SkillTarget(val agents: String, val skills: Path) {
    val directory: Path get() = skills.resolve(SkillBundle.NAME)
}
