package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.generator.RepositorySpec
import io.github.krosstack.kscope.analyzer.schema.web.BundleWorld
import io.github.krosstack.kscope.analyzer.schema.web.TASKTIDE_MANIFEST
import io.github.krosstack.kscope.analyzer.schema.web.TASKTIDE_VITE_CONFIG

internal const val UNDECLARED_MANIFEST = """{"name": "@tasktide/web", "devDependencies": {"vite": "5.4.10"}}"""

internal val declared = mapOf("package.json" to TASKTIDE_MANIFEST, "vite.config.ts" to TASKTIDE_VITE_CONFIG)

internal fun viteConfig(outDir: String) = "export default { build: { outDir: \"$outDir\" } };\n"

internal fun manifest(name: String) = """{"name": "$name", "devDependencies": {"vite": "5.4.10"}}"""

internal fun copyScript(vararg paths: String, into: String? = "static"): String = paths.joinToString("\n") { path ->
    val block = into?.let { " {\n        into(\"$it\")\n    }" }.orEmpty()
    "tasks.processResources {\n    from(\"\$rootDir/../$path\")$block\n}"
}

private fun app(script: String) = RepositorySpec(
    "app",
    mapOf("build.gradle.kts" to "", "server/build.gradle.kts" to script),
    listOf(ModuleSpec(":"), ModuleSpec(":server")),
)

internal fun repository(name: String, files: Map<String, String>, vararg modules: ModuleSpec) =
    RepositorySpec(name, files, modules.toList().ifEmpty { listOf(ModuleSpec(":", roots = listOf(""))) })

internal fun kotlinWeb(vararg modules: ModuleSpec, files: Map<String, String> = emptyMap()) =
    repository("kmp", mapOf("build.gradle.kts" to "") + files, ModuleSpec(":"), *modules)

internal fun world(script: String, vararg others: RepositorySpec) = BundleWorld(listOf(app(script)) + others)
