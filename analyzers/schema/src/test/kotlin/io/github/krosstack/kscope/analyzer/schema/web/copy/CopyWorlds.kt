package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.generator.RepositorySpec
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.analyzer.schema.web.BundleWorld
import io.github.krosstack.kscope.analyzer.schema.web.TASKTIDE_MANIFEST
import io.github.krosstack.kscope.analyzer.schema.web.TASKTIDE_VITE_CONFIG
import io.github.krosstack.kscope.model.Symbol

internal const val SERVER_SCRIPT = """plugins {
    kotlin("jvm")
}

tasks.processResources {
    from("${'$'}rootDir/../tasktide-web/dist") {
        into("static")
    }
}
"""

internal const val SERVER = "server/build.gradle.kts"

private val webFiles = mapOf("package.json" to TASKTIDE_MANIFEST, "vite.config.ts" to TASKTIDE_VITE_CONFIG)

/**
 * The repositories `tasktide-app`, with the modules `:` and `:server` and the script of `:server` given, and
 * `tasktide-web`, with one module that holds all its files, beside the repositories in [extra].
 */
internal fun tasktideWorld(
    server: String = SERVER_SCRIPT,
    extra: List<RepositorySpec> = emptyList(),
    appFiles: Map<String, String> = emptyMap(),
) = BundleWorld(
    listOf(
        RepositorySpec(
            "tasktide-app",
            mapOf("build.gradle.kts" to "", SERVER to server) + appFiles,
            listOf(ModuleSpec(":"), ModuleSpec(":server")),
        ),
        RepositorySpec("tasktide-web", webFiles, listOf(ModuleSpec(":", roots = listOf("")))),
    ) + extra,
)

internal fun copying(vararg paths: String): String =
    paths.joinToString("\n", "tasks.processResources {\n", "\n}\n") { "    from(\"$it\")" }

internal fun BundleWorld.sources(): List<Symbol> = copyFacts.symbols()

internal fun Symbol.place(): Pair<String?, String?> = attributes["copy.repository"] to attributes["copy.path"]

internal fun Symbol.file(): String =
    "${nameLocation?.artifact?.repository?.value}/${nameLocation?.artifact?.path?.value}"
