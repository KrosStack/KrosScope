package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.engine.scope.ScanPattern
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult

/** Checks the values of the `scan` table, lists of patterns and the switch for tests, and of the analyzer switches. */
internal class SettingsScopeValidation(private val toml: TomlParseResult, private val present: Set<List<String>>) {
    fun issues(): List<SettingsIssue> = patternIssues() + switchIssues()

    private fun patternIssues(): List<SettingsIssue> =
        SettingsGrammar.PATTERN_LISTS.filter { it in present }.flatMap(::listIssues)

    private fun listIssues(path: List<String>): List<SettingsIssue> {
        val array = toml.get(path) as? TomlArray
        return when {
            array == null -> listOf(issueAt(path, "must be an array of strings"))

            array.size() > SettingsGrammar.MAXIMUM_PATTERNS ->
                listOf(issueAt(path, "must have at most ${SettingsGrammar.MAXIMUM_PATTERNS} patterns"))

            else -> (0 until array.size()).mapNotNull { elementIssue(path, array, it) }
        }
    }

    private fun elementIssue(path: List<String>, array: TomlArray, index: Int): SettingsIssue? {
        val problem = when (val element = array.get(index)) {
            is String -> ScanPattern.problemWith(element)
            else -> "must be a string"
        }
        return problem?.let {
            SettingsIssue(array.inputPositionOf(index)?.line(), "${path.joinToString(".")}[$index]", it)
        }
    }

    private fun switchIssues(): List<SettingsIssue> = SettingsGrammar.BOOLEANS.filter { it in present }
        .filter { toml.get(it) !is Boolean }
        .map { issueAt(it, "must be true or false") }

    private fun issueAt(path: List<String>, message: String) =
        SettingsIssue(toml.inputPositionOf(path)?.line(), path.joinToString("."), message)
}
