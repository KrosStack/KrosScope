package io.github.krosstack.kscope.cli.skill

/** One file of the skill: its path inside the skill folder and its text. */
internal data class SkillFile(val path: String, val text: String)

/** The files of the kscope skill that ship inside the tool, with the version of the tool written into them. */
internal class SkillBundle(private val version: String) {
    fun files(): List<SkillFile> = PATHS.map { SkillFile(it, text(it)) }

    fun skill(): String = text(SKILL_FILE)

    private fun text(path: String): String {
        val stream = checkNotNull(SkillBundle::class.java.getResourceAsStream("$ROOT/$path")) {
            "The skill file $path is missing from the distribution."
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }.replace(VERSION, version)
    }

    companion object {
        const val NAME = "kscope"
        const val SKILL_FILE = "SKILL.md"
        val PATHS = listOf(SKILL_FILE, "references/commands.md")

        private const val ROOT = "/io/github/krosstack/kscope/cli/skill/kscope"
        private const val VERSION = "{{kscope-version}}"
    }
}
