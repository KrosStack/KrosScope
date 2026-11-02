package io.github.krosstack.kscope.analyzer.android.resources

import io.github.krosstack.kscope.analyzer.android.ResourceLocation
import io.github.krosstack.kscope.analyzer.android.layout.LayoutClassScan
import io.github.krosstack.kscope.analyzer.android.linking.ClassMention
import io.github.krosstack.kscope.analyzer.android.xml.XmlDocument
import io.github.krosstack.kscope.analyzer.android.xml.XmlReader
import io.github.krosstack.kscope.model.TextRange

private const val VALUES = "values"
private const val LAYOUT = "layout"
private const val NAVIGATION = "navigation"
private const val ROOT = "resources"
private const val BYTE_ORDER_MARK = '﻿'

/** Reads one resource file into the resources it defines and the references it contains. */
internal class ResourceFileReader {
    private val xml = XmlReader()

    fun read(location: ResourceLocation, content: ByteArray, lineCount: Int): ParsedResourceFile = when {
        !location.isKnownType -> ParsedResourceFile.empty
        location.type == VALUES -> if (location.isXml) readValues(content) else ParsedResourceFile.empty
        location.isXml -> readFileResource(location, content)
        else -> binaryFileResource(location, lineCount)
    }

    private fun readValues(content: ByteArray): ParsedResourceFile {
        val document = xml.parse(textOf(content))
        val collector = ResourceCollector()
        val root = document.root
        if (root != null && root.name == ROOT) ValuesReader(document, collector).read(root)
        return collector.finish(document.error)
    }

    private fun readFileResource(location: ResourceLocation, content: ByteArray): ParsedResourceFile {
        val document = xml.parse(textOf(content))
        val collector = ResourceCollector()
        val key = ResourceKey(location.type, location.baseName)
        collector.define(ResourceDefinition(key, document.wholeFile(), FILE_START))
        document.root?.let { ReferenceScan(document, collector).scan(it, key) }
        return collector.finish(document.error, classMentionsOf(location, document))
    }

    private fun classMentionsOf(location: ResourceLocation, document: XmlDocument): List<ClassMention> {
        val root = document.root
        val isLayout = location.type == LAYOUT
        return if (root != null && (isLayout || location.type == NAVIGATION)) {
            LayoutClassScan(document).mentions(root, isNavigation = !isLayout)
        } else {
            emptyList()
        }
    }

    private fun binaryFileResource(location: ResourceLocation, lineCount: Int): ParsedResourceFile {
        val key = ResourceKey(location.type, location.baseName)
        val whole = TextRange(1, 1, lineCount.coerceAtLeast(1) + 1, 1)
        return ParsedResourceFile(listOf(ResourceDefinition(key, whole, FILE_START)), emptyList(), 0, null)
    }

    private fun textOf(content: ByteArray): String = content.toString(Charsets.UTF_8).removePrefix("$BYTE_ORDER_MARK")

    private companion object {
        val FILE_START = TextRange(1, 1, 1, 1)
    }
}
