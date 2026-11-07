package io.github.krosstack.kscope.conventions

internal object ModuleDependencyRules {
    val confinements = mapOf("com.microsoft.onnxruntime:onnxruntime" to ":components:semantic-worker")

    private val rules = listOf(
        rule(":core:model"),
        rule(":core:spi", ":core:model"),
        rule(":core:store", ":core:model", ":core:spi"),
        rule(":core:engine", ":core:model", ":core:spi", ":core:store"),
        rule(":components:semantic-worker", ":core:model", ":core:spi"),
        rule(":analyzers:gradle", ":core:model", ":core:spi", ":analyzers:gradle-model-plugin"),
        rule(":analyzers:kotlin", ":core:model", ":core:spi", ":analyzers:kotlin-worker"),
        rule(":analyzers:*", ":core:model", ":core:spi"),
        rule(":intelligence:*", ":core:model", ":core:spi"),
        rule(":service", ":core:*", ":analyzers:*", ":intelligence:*"),
        rule(":cli", ":service", ":benchmark", ":analyzers:kotlin-worker"),
        rule(":benchmark", ":service", ":core:model", ":core:spi", ":analyzers:kotlin-worker"),
        rule(":tools:detekt-rules"),
    )

    fun violations(modulePath: String, projectDependencies: Collection<String>): List<String> {
        val rule = rules.firstOrNull { it.appliesTo(modulePath) }
            ?: return listOf("$modulePath has no entry in ModuleDependencyRules")
        return projectDependencies.sorted().distinct()
            .filterNot(rule::permits)
            .map { "$modulePath must not depend on $it" }
    }

    private fun rule(modulePattern: String, vararg allowed: String) =
        DependencyRule(PathPattern(modulePattern), allowed.map(::PathPattern))
}

private class DependencyRule(private val module: PathPattern, private val allowed: List<PathPattern>) {
    fun appliesTo(modulePath: String) = module.matches(modulePath)

    fun permits(dependencyPath: String) = allowed.any { it.matches(dependencyPath) }
}

private class PathPattern(private val pattern: String) {
    private val wildcardPrefix = pattern.removeSuffix(WILDCARD).takeIf { pattern.endsWith(WILDCARD) }

    fun matches(path: String): Boolean {
        if (wildcardPrefix == null) return path == pattern
        val tail = path.removePrefix(wildcardPrefix)
        return path.startsWith(wildcardPrefix) && tail.isNotEmpty() && ':' !in tail
    }

    private companion object {
        const val WILDCARD = "*"
    }
}
