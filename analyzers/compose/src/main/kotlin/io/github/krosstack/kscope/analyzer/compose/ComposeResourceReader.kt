package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.TextRange

private const val BYTE_ORDER_MARK = '\uFEFF'
private val fileStart = TextRange(1, 1, 1, 1)

/** Reads one resource file into the resources it defines. */
internal class ComposeResourceReader {
    private val scanner = ValuesScanner()

    fun read(location: ComposeResourceLocation, content: ByteArray, lineCount: Int): ParsedResourceFile =
        when (location.kind) {
            ResourceFolder.VALUES -> readValues(content)
            ResourceFolder.DRAWABLE -> fileResource(ResourceType.DRAWABLE, location, lineCount)
            ResourceFolder.FONT -> fileResource(ResourceType.FONT, location, lineCount)
            ResourceFolder.FILES -> ParsedResourceFile(emptyList())
        }

    private fun readValues(content: ByteArray): ParsedResourceFile {
        val scanned = scanner.scan(content.toString(Charsets.UTF_8).removePrefix("$BYTE_ORDER_MARK"))
        val definitions = scanned.elements.mapNotNull { element ->
            ResourceType.ofValuesElement(element.tag)?.let {
                ResourceDefinition(ResourceKey(it, element.name), element.declaration, element.nameRange)
            }
        }
        return ParsedResourceFile(definitions, scanned.problem)
    }

    // The name of a binary resource is its file name and the whole file declares it, as in the plugin.
    private fun fileResource(type: ResourceType, location: ComposeResourceLocation, lines: Int): ParsedResourceFile {
        val whole = TextRange(1, 1, lines.coerceAtLeast(1) + 1, 1)
        return ParsedResourceFile(listOf(ResourceDefinition(ResourceKey(type, location.baseName), whole, fileStart)))
    }
}
