package io.github.krosstack.kscope.analyzer.android.resources

import io.github.krosstack.kscope.analyzer.android.xml.XmlAttribute
import io.github.krosstack.kscope.analyzer.android.xml.XmlDocument
import io.github.krosstack.kscope.analyzer.android.xml.XmlNode

private const val STYLE_PARENT = "style-parent"

internal fun XmlDocument.nameRange(name: XmlAttribute) = range(name.valueStart, name.valueStart + name.value.length)

/** Reads a `style` element: its parent, explicit or implied by its dotted name, and the references in its items. */
internal class StyleReader(
    private val document: XmlDocument,
    private val collector: ResourceCollector,
    private val scan: ReferenceScan,
) {
    fun read(node: XmlNode, key: ResourceKey, name: XmlAttribute) {
        val parent = node.attribute("parent")
        when {
            parent == null -> implicitParent(key, name)
            parent.value.isNotBlank() -> explicitParent(node, parent, key)
        }
        scan.scan(node, key, except = "parent")
    }

    // `TaskTitle.Done` extends `TaskTitle` unless it says otherwise.
    private fun implicitParent(key: ResourceKey, name: XmlAttribute) {
        if ('.' !in key.name) return
        val parent = ResourceKey("style", key.name.substringBeforeLast('.'))
        collector.use(ResourceUse(key, parent, "$STYLE_PARENT.implicit", document.nameRange(name)))
    }

    private fun explicitParent(node: XmlNode, parent: XmlAttribute, key: ResourceKey) {
        val value = parent.value.trim()
        when {
            value.startsWith('@') || value.startsWith('?') -> scan.attribute(node, parent, key, STYLE_PARENT)

            ':' in value -> collector.external()

            else -> {
                val target = ResourceKey("style", value)
                collector.use(ResourceUse(key, target, STYLE_PARENT, document.nameRange(parent)))
            }
        }
    }
}
