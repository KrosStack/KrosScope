package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.web.WebWorkspace
import io.github.krosstack.kscope.analyzer.schema.web.manifest.PackageManifestReader

internal const val PLAIN_MANIFEST = """{"name": "app", "version": "1.0.0"}"""

internal fun outputOf(files: Map<String, String>, manifest: String = "package.json"): String? {
    val workspace = WebWorkspace(files)
    val parsed = checkNotNull(PackageManifestReader.read(files.getValue(manifest)))
    val output = BundleOutputReader(workspace.artifacts, workspace.files).outputOf(workspace.artifact(manifest), parsed)
    return output?.let { "${it.bundler.id} ${it.directory} ${if (it.isDeclared) "declared" else "default"}" }
}

internal fun viteConfig(body: String) =
    "import { defineConfig } from \"vite\";\nexport default defineConfig({ $body });\n"

internal fun webpackConfig(body: String) = "const path = require(\"path\");\nmodule.exports = { $body };\n"

internal fun rollupConfig(body: String) = "export default { input: \"src/main.js\", $body };\n"

internal fun withConfig(name: String, text: String, directory: String = "") = mapOf(
    "$directory/package.json".removePrefix("/") to PLAIN_MANIFEST,
    "$directory/$name".removePrefix("/") to text,
)
