package io.github.krosstack.kscope.gradle.model

/** The Kotlin Gradle plugin extension: source sets with directories and `dependsOn` edges, targets, compilations. */
internal object KotlinSection {
    fun describe(extension: Any, includeSourceSets: Boolean): Map<String, Any?> = linkedMapOf(
        "kgpVersion" to Values.text(Reflect.get(extension, "coreLibrariesVersion")),
        "sourceSets" to if (includeSourceSets) sourceSets(extension) else emptyList<Any>(),
        "targets" to targets(extension).map(::target),
    )

    fun targets(extension: Any): List<Any> {
        val property = if (Reflect.has(extension, "targets")) "targets" else "target"
        return Values.items(Reflect.get(extension, property)).filterNotNull()
            .sortedBy { Values.text(Reflect.get(it, "name")) }
    }

    fun compilations(target: Any): List<Any> = Values.items(Reflect.get(target, "compilations")).filterNotNull()

    private fun sourceSets(extension: Any): List<Map<String, Any?>> =
        Values.items(Reflect.get(extension, "sourceSets")).filterNotNull().map { sourceSet ->
            linkedMapOf(
                "name" to Values.text(Reflect.get(sourceSet, "name")),
                "kotlinDirs" to SourceDirs.of(Reflect.get(sourceSet, "kotlin")),
                "resourceDirs" to SourceDirs.of(Reflect.get(sourceSet, "resources")),
                "dependsOn" to Values.names(Reflect.get(sourceSet, "dependsOn")),
            )
        }.sortedBy { it["name"].toString() }

    private fun target(target: Any): Map<String, Any?> = linkedMapOf(
        "name" to Values.text(Reflect.get(target, "name")),
        "platformType" to Values.text(Reflect.get(target, "platformType")),
        "compilations" to compilations(target).map(::compilation).sortedBy { it["name"].toString() },
    )

    private fun compilation(compilation: Any): Map<String, Any?> = linkedMapOf(
        "name" to Values.text(Reflect.get(compilation, "name")),
        "defaultSourceSet" to Values.text(Reflect.get(Reflect.get(compilation, "defaultSourceSet"), "name")),
        "allSourceSets" to Values.names(Reflect.get(compilation, "allKotlinSourceSets")),
        "associatedWith" to Values.names(Reflect.get(compilation, "associatedCompilations")),
    )
}
