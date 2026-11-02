package io.github.krosstack.kscope.analyzer.gradle

import java.nio.file.Path

private fun Path.slashed() = toString().replace('\\', '/')

private fun projectDocument(buildRoot: Path, project: Path, path: String, included: Boolean, schema: Int): String {
    val main = project.resolve("src/main/kotlin")
    return """
        {"schema":$schema,"path":"$path","name":"app","dir":"${project.slashed()}","buildDir":"${project.slashed()}/build",
        "build":{"name":"space","rootDir":"${buildRoot.slashed()}","included":$included,"gradleVersion":"9.5.1"},
        "plugins":["org.jetbrains.kotlin.jvm","java"],
        "kotlin":{"sourceSets":[{"name":"main","kotlinDirs":["${main.slashed()}"],"resourceDirs":[],"dependsOn":[]}],
        "targets":[{"name":"kotlin","platformType":"jvm","compilations":[{"name":"main","allSourceSets":["main"]}]}]},
        "java":[{"name":"main","javaDirs":[],"resourceDirs":[]}],
        "classpaths":{"files":["/libs/stdlib.jar"],"entries":[{"kind":"java","name":"main","sourceSets":["main"],"files":[0]}]},
        "errors":{}}
    """.trimIndent().replace("\n", "")
}

/** The array of project documents that a fetch of a small workspace with the project `:app` returns. */
internal fun documentsOf(root: Path, includedBuild: String? = null, schema: Int = SUPPORTED_SCHEMA): String {
    val app = projectDocument(root, root.resolve("app"), ":app", included = false, schema = schema)
    val included = includedBuild?.let { name ->
        val directory = root.resolve(name)
        projectDocument(directory, directory, ":", included = true, schema = schema)
    }
    return listOfNotNull(app, included).joinToString(",", "[", "]")
}
