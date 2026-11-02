package io.github.krosstack.kscope.analyzer.schema.web.manifest

import io.github.krosstack.kscope.analyzer.schema.document.DocEntry
import io.github.krosstack.kscope.analyzer.schema.document.DocMapping
import io.github.krosstack.kscope.analyzer.schema.document.DocScalar
import io.github.krosstack.kscope.analyzer.schema.document.FlowReader
import io.github.krosstack.kscope.analyzer.schema.web.Bundler

private const val BYTE_ORDER_MARK = "\uFEFF"
private val dependencyKeys = listOf("dependencies", "devDependencies")
private val commandSeparators = Regex("[\\s;&|()]+")

/** Reads a `package.json`. Text that is not a JSON object has no value. */
internal object PackageManifestReader {
    fun read(text: String): PackageManifest? {
        val root = FlowReader(text.removePrefix(BYTE_ORDER_MARK), 0).read() as? DocMapping ?: return null
        val tools = toolsOf(root)
        return PackageManifest(
            name = (root.entry("name")?.value as? DocScalar)?.takeIf { it.text.isNotBlank() },
            version = root.text("version")?.takeIf { it.isNotBlank() },
            namedBundler = Bundler.entries.firstOrNull { it.defaultDirectory != null && it.id in tools },
        )
    }

    private fun toolsOf(root: DocMapping): Set<String> {
        val dependencies = dependencyKeys.flatMap { root.mapping(it)?.entries.orEmpty() }.map(DocEntry::key)
        val commands = root.mapping("scripts")?.entries.orEmpty()
            .flatMap { (it.value as? DocScalar)?.text.orEmpty().split(commandSeparators) }
        return (dependencies + commands).toSet()
    }
}
