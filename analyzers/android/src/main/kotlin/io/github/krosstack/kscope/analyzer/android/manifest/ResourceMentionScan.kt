package io.github.krosstack.kscope.analyzer.android.manifest

import io.github.krosstack.kscope.analyzer.android.resources.ReferenceToken
import io.github.krosstack.kscope.analyzer.android.resources.ReferenceTokens
import io.github.krosstack.kscope.analyzer.android.xml.XmlDocument
import io.github.krosstack.kscope.analyzer.android.xml.XmlNode

/** Finds the resources named by the attributes of an element and of the elements below it. */
internal class ResourceMentionScan(private val document: XmlDocument) {
    /** Reads [node] and its descendants, except the subtrees for which [skip] is true. */
    fun of(node: XmlNode, skip: (XmlNode) -> Boolean = { false }): ResourceMentions {
        val tokens = nodesOf(node, skip).flatMap { element ->
            element.attributes.map { attribute -> attribute to ReferenceTokens.parse(attribute.value) }
        }
        val resources = tokens.mapNotNull { (attribute, token) ->
            (token as? ReferenceToken.Resource)?.let {
                val begin = attribute.valueStart + it.start
                ResourceMention(it.key, document.range(begin, begin + attribute.value.trim().length))
            }
        }
        return ResourceMentions(resources, tokens.count { it.second == ReferenceToken.External })
    }

    private fun nodesOf(node: XmlNode, skip: (XmlNode) -> Boolean): List<XmlNode> =
        listOf(node) + node.children.filterNot(skip).flatMap { nodesOf(it, skip) }
}
