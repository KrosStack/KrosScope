package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind

/**
 * The kinds and attributes of the symbols of web bundles, which the linkers read. An npm package records the directory
 * that its bundler writes to, and whether its config says so or the bundler's default applies. A copy source records
 * the Gradle task that copies from a path, and where the path lies when a repository of the workspace holds it.
 */
internal object WebKinds {
    const val SOURCE_MAP_SUB_KIND = "source-map"
    const val WEB_BUNDLE_SUB_KIND = "web-bundle"
    const val AMBIGUOUS_PRODUCER = "artifact.ambiguous-producer"
    const val VERSION_ATTRIBUTE = "npm.version"
    const val OUTPUT_ATTRIBUTE = "bundle.output"
    const val OUTPUT_DECLARED_ATTRIBUTE = "bundle.output-declared"
    const val BUNDLER_ATTRIBUTE = "bundle.bundler"
    const val COPY_TASK_ATTRIBUTE = "copy.task"
    const val COPY_INTO_ATTRIBUTE = "copy.into"
    const val COPY_REPOSITORY_ATTRIBUTE = "copy.repository"
    const val COPY_PATH_ATTRIBUTE = "copy.path"
    const val BUNDLE_INTO_ATTRIBUTE = "bundle.into"

    val npmPackage = SymbolKind("npm.package", SymbolCategory.BUILD)
    val copySource = SymbolKind("gradle.copy-source", SymbolCategory.BUILD)
    val bundleFile = SymbolKind("bundle.file", SymbolCategory.BUILD)
    val typescriptFile = SymbolKind("typescript.file", SymbolCategory.PACKAGE)
    val javascriptFile = SymbolKind("javascript.file", SymbolCategory.PACKAGE)

    fun sourceFileKind(type: ArtifactType): SymbolKind =
        if (type == WebArtifactTypes.typescript) typescriptFile else javascriptFile
}
