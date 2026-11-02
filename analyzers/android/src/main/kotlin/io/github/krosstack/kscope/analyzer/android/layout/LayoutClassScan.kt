package io.github.krosstack.kscope.analyzer.android.layout

import io.github.krosstack.kscope.analyzer.android.linking.ClassMention
import io.github.krosstack.kscope.analyzer.android.resources.ANDROID_NAMESPACE
import io.github.krosstack.kscope.analyzer.android.resources.nameRange
import io.github.krosstack.kscope.analyzer.android.xml.XmlDocument
import io.github.krosstack.kscope.analyzer.android.xml.XmlNode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.TextRange

internal const val TOOLS_NAMESPACE = "http://schemas.android.com/tools"
internal const val AUTO_NAMESPACE = "http://schemas.android.com/apk/res-auto"

private val navigationDestinations = setOf("fragment", "activity", "dialog")
private const val TAG_TERMINATORS = " \t\r\n/>"

/**
 * Finds the classes that a layout or a navigation graph names: custom view tags, `class` of `<view>`, the fragment of
 * `<fragment>` and of a fragment container, `tools:context`, `app:layoutManager`, and the destinations of a graph.
 * Each kind has its own sub-kind, so that a reader can tell what the reference means.
 */
internal class LayoutClassScan(private val document: XmlDocument) {
    fun mentions(root: XmlNode, isNavigation: Boolean): List<ClassMention> =
        nodesOf(root).flatMap { node -> if (isNavigation) destinationOf(node) else layoutMentions(node) }

    private fun nodesOf(node: XmlNode): List<XmlNode> = listOf(node) + node.children.flatMap(::nodesOf)

    private fun layoutMentions(node: XmlNode): List<ClassMention> = listOfNotNull(
        customTag(node),
        viewClass(node),
        fragmentName(node),
        attributeMention(node, TOOLS_NAMESPACE, "context", "tools-context"),
        layoutManager(node),
    )

    private fun customTag(node: XmlNode): ClassMention? {
        if ('.' !in node.name) return null
        val begin = node.startOffset + 1
        val end = (begin until document.text.length).firstOrNull { document.text[it] in TAG_TERMINATORS }
        return mention(node.name, "custom-view", document.range(begin, end ?: document.text.length))
    }

    private fun viewClass(node: XmlNode): ClassMention? =
        if (node.name == "view") attributeMention(node, null, "class", "view-class") else null

    private fun fragmentName(node: XmlNode): ClassMention? {
        val isFragment = node.name == "fragment" || node.name.endsWith("FragmentContainerView")
        return if (isFragment) attributeMention(node, ANDROID_NAMESPACE, "name", "fragment-class") else null
    }

    // RecyclerView reads a name that starts with a dot relative to the application id, which the build model lacks.
    private fun layoutManager(node: XmlNode): ClassMention? {
        val attribute = node.attribute("layoutManager", AUTO_NAMESPACE) ?: return null
        val value = attribute.value.trim()
        val isQualified = '.' in value && !value.startsWith('.')
        return if (isQualified) mention(value, "layout-manager", document.nameRange(attribute)) else null
    }

    private fun destinationOf(node: XmlNode): List<ClassMention> {
        val name = attributeMention(node, ANDROID_NAMESPACE, "name", "destination-class")
        return if (node.name in navigationDestinations) listOfNotNull(name) else emptyList()
    }

    private fun attributeMention(node: XmlNode, namespace: String?, name: String, subKind: String): ClassMention? =
        node.attribute(name, namespace)?.takeIf { it.value.isNotBlank() }
            ?.let { mention(it.value, subKind, document.nameRange(it)) }

    private fun mention(written: String, subKind: String, range: TextRange) =
        ClassMention(written, RelationKind.REFERENCES, subKind, range)
}
