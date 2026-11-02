package io.github.krosstack.kscope.benchmark.gold

/**
 * Checks that gold is consistent with itself and with the files of the workspace it describes, so that a stale gold
 * file is found before it distorts a measurement. [lineCounts] maps each file of the workspace to its number of lines.
 */
public class GoldValidator(private val lineCounts: Map<String, Int>) {
    public fun problems(gold: GoldSet): List<String> =
        (relationshipProblems(gold) + taskProblems(gold) + closedWorldProblems(gold)).sorted()

    private fun relationshipProblems(gold: GoldSet): List<String> {
        val ids = gold.relationships.relationships.map { it.id }
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.map { "duplicate id $it" }
        val files = gold.relationships.relationships.flatMap { relationship ->
            listOf(relationship.from, relationship.to).mapNotNull { address -> missingFile(relationship.id, address) }
        }
        return duplicates + files
    }

    private fun taskProblems(gold: GoldSet): List<String> {
        val ids = gold.relationships.relationships.map { it.id }.toSet()
        return gold.tasks.flatMap { task ->
            val unknown = task.relationships.filterNot { it in ids }.map { "${task.id}: unknown relationship $it" }
            val read = (task.mustEdit + task.mayEdit + task.mustRead).filterNot { it in lineCounts }
            unknown + read.map { "${task.id}: no such file $it" } + task.symbols.mapNotNull { missingFile(task.id, it) }
        }
    }

    private fun closedWorldProblems(gold: GoldSet): List<String> = gold.closedWorld.flatMap { file ->
        val lines = lineCounts[file.file] ?: return@flatMap listOf("${file.file}: no such file")
        val outside = file.edges.map { it.line } + file.imports.map { it.line } + file.declarations.map { it.line }
        listOfNotNull(
            "${file.file}: no declarations".takeIf { file.declarations.isEmpty() },
            "${file.file}: a line lies outside the file".takeIf { outside.any { line -> line < 1 || line > lines } },
        )
    }

    private fun missingFile(owner: String, address: GoldAddress): String? =
        address.file?.takeIf { it !in lineCounts }?.let { "$owner: no such file $it" }
}
