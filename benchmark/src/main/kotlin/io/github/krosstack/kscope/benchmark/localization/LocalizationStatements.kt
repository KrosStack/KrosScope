package io.github.krosstack.kscope.benchmark.localization

internal object LocalizationStatements {
    fun validationErrors(tasks: List<LocalizationTask>, statements: List<LocalizationStatement>): List<String> {
        val ids = tasks.map { it.id }
        val heldOutIds = statements.map { it.id }
        return buildList {
            if (ids.isEmpty() || ids.distinct().size != ids.size || ids.any { it.isBlank() }) {
                add("Original task IDs must be nonempty and unique.")
            }
            if (heldOutIds.distinct().size != heldOutIds.size) add("Held-out statement IDs must be unique.")
            if (heldOutIds.toSet() != ids.toSet()) {
                add("Held-out statements must exactly match original task IDs.")
            }
            if (statements.any { it.id.isBlank() || it.statement.isBlank() }) {
                add("Held-out statement IDs and statements must be nonblank.")
            }
        }
    }
}
