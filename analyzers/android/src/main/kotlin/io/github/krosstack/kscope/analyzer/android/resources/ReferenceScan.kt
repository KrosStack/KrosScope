package io.github.krosstack.kscope.analyzer.android.resources

import io.github.krosstack.kscope.analyzer.android.xml.XmlAttribute
import io.github.krosstack.kscope.analyzer.android.xml.XmlDocument
import io.github.krosstack.kscope.analyzer.android.xml.XmlNode
import io.github.krosstack.kscope.analyzer.android.xml.XmlText

internal const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

private val navigationAttributes = setOf("destination", "startDestination", "popUpTo")

/**
 * Finds the references in the attributes and texts of an element tree and the ids that `android:id="@+id/name"`
 * declares. Every reference found belongs to the resource [ResourceCollector] is told it is part of.
 */
internal class ReferenceScan(private val document: XmlDocument, private val collector: ResourceCollector) {
    /** Scans [node] and everything below it; [except] names an attribute of [node] itself that the caller handles. */
    fun scan(node: XmlNode, owner: ResourceKey, except: String? = null) {
        node.attributes.filter { it.name != except || it.namespace != null }.forEach { attribute(node, it, owner) }
        node.texts.forEach { text(it, owner) }
        node.children.forEach { scan(it, owner) }
    }

    /** Reads one attribute value; [subKind] overrides what the attribute would say about the reference. */
    fun attribute(node: XmlNode, attribute: XmlAttribute, owner: ResourceKey, subKind: String? = null) {
        when (val token = ReferenceTokens.parse(attribute.value)) {
            is ReferenceToken.Resource -> {
                val declares = declaresId(token)
                if (declares) declareId(node, attribute, token)
                if (!declares || !isIdAttribute(attribute)) {
                    use(attribute.valueStart, attribute.value, token, owner, subKind ?: subKindOf(node, attribute))
                }
            }

            ReferenceToken.External -> collector.external()

            ReferenceToken.None -> Unit
        }
    }

    private fun text(text: XmlText, owner: ResourceKey) {
        when (val token = ReferenceTokens.parse(text.value)) {
            is ReferenceToken.Resource -> use(text.start, text.value, token, owner, null)
            ReferenceToken.External -> collector.external()
            ReferenceToken.None -> Unit
        }
    }

    private fun use(valueStart: Int, value: String, token: ReferenceToken.Resource, owner: ResourceKey, kind: String?) {
        val begin = valueStart + token.start
        collector.use(ResourceUse(owner, token.key, kind, document.range(begin, begin + value.trim().length)))
    }

    private fun declaresId(token: ReferenceToken.Resource) = token.declares && token.key.type == "id"

    private fun isIdAttribute(attribute: XmlAttribute) =
        attribute.namespace == ANDROID_NAMESPACE && attribute.name == "id"

    private fun declareId(node: XmlNode, attribute: XmlAttribute, token: ReferenceToken.Resource) {
        val nameStart = attribute.valueStart + token.nameStart
        val nameRange = document.range(nameStart, nameStart + token.key.name.length)
        val declaration = document.range(node.startOffset, node.endOffset)
        val isByReference = !isIdAttribute(attribute)
        collector.define(ResourceDefinition(token.key, declaration, nameRange, isByReference = isByReference))
    }

    private fun subKindOf(node: XmlNode, attribute: XmlAttribute): String? = when {
        node.name == "include" && attribute.namespace == null && attribute.name == "layout" -> "include"
        attribute.name in navigationAttributes -> "navigation-destination"
        else -> null
    }
}
