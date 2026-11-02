package io.github.krosstack.kscope.analyzer.android.manifest

import io.github.krosstack.kscope.analyzer.android.linking.ClassMention
import io.github.krosstack.kscope.analyzer.android.resources.ANDROID_NAMESPACE
import io.github.krosstack.kscope.analyzer.android.resources.nameRange
import io.github.krosstack.kscope.analyzer.android.xml.XmlDocument
import io.github.krosstack.kscope.analyzer.android.xml.XmlNode
import io.github.krosstack.kscope.analyzer.android.xml.XmlReader
import io.github.krosstack.kscope.model.RelationKind

private const val APPLICATION = "application"
private const val ACTIVITY_ALIAS = "activity-alias"
private const val PERMISSION = "permission"
private const val BYTE_ORDER_MARK = '\uFEFF'
private val componentTags = setOf("activity", ACTIVITY_ALIAS, "service", "receiver", "provider")

/** Reads an `AndroidManifest.xml` into the elements that declare something and the references they make. */
internal class ManifestReader {
    private val xml = XmlReader()

    fun read(content: ByteArray): ParsedManifest {
        val document = xml.parse(content.toString(Charsets.UTF_8).removePrefix("$BYTE_ORDER_MARK"))
        val root = document.root?.takeIf { it.name == "manifest" } ?: return ParsedManifest.failed(document.error)
        val elements = ElementReader(document)
        val applications = root.children.filter { it.name == APPLICATION }.flatMap(elements::application)
        val permissions = root.children.filter { it.name == PERMISSION }.map(elements::component)
        return ParsedManifest(root.attribute("package")?.value, applications + permissions, document.error)
    }
}

private class ElementReader(private val document: XmlDocument) {
    private val resources = ResourceMentionScan(document)

    /** The application and the components below it; a component's own elements are not the application's. */
    fun application(node: XmlNode): List<ManifestComponent> {
        val own = componentOf(node) { it.name in componentTags }
        return listOf(own) + node.children.filter { it.name in componentTags }.map(::component)
    }

    fun component(node: XmlNode): ManifestComponent = componentOf(node) { false }

    private fun componentOf(node: XmlNode, skip: (XmlNode) -> Boolean): ManifestComponent {
        val name = node.attribute("name", ANDROID_NAMESPACE)
        return ManifestComponent(
            tag = node.name,
            written = name?.value?.trim(),
            nameRange = name?.let(document::nameRange),
            declaration = document.range(node.startOffset, node.endOffset),
            exported = node.attribute("exported", ANDROID_NAMESPACE)?.value,
            actions = actionsOf(node),
            classMention = classOf(node),
            uses = resources.of(node, skip),
        )
    }

    private fun actionsOf(node: XmlNode): List<String> = node.children.filter { it.name == "intent-filter" }
        .flatMap { filter -> filter.children.filter { it.name == "action" } }
        .mapNotNull { it.attribute("name", ANDROID_NAMESPACE)?.value?.trim() }
        .distinct().sorted()

    // An alias is named for itself and implemented by its target; every other component by the class it names.
    private fun classOf(node: XmlNode): ClassMention? {
        val attribute = when (node.name) {
            ACTIVITY_ALIAS -> node.attribute("targetActivity", ANDROID_NAMESPACE)
            APPLICATION, in componentTags -> node.attribute("name", ANDROID_NAMESPACE)
            else -> null
        } ?: return null
        val subKind = if (node.name == ACTIVITY_ALIAS) "target-activity" else null
        return ClassMention(attribute.value, RelationKind.BINDS, subKind, document.nameRange(attribute))
    }
}
