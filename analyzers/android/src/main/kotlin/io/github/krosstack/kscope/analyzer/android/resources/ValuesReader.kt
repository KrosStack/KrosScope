package io.github.krosstack.kscope.analyzer.android.resources

import io.github.krosstack.kscope.analyzer.android.xml.XmlAttribute
import io.github.krosstack.kscope.analyzer.android.xml.XmlDocument
import io.github.krosstack.kscope.analyzer.android.xml.XmlNode

private val typeOfTag = mapOf(
    "string" to "string",
    "plurals" to "plurals",
    "string-array" to "array",
    "integer-array" to "array",
    "array" to "array",
    "color" to "color",
    "dimen" to "dimen",
    "bool" to "bool",
    "integer" to "integer",
    "fraction" to "fraction",
    "drawable" to "drawable",
    "style" to "style",
    "attr" to "attr",
    "declare-styleable" to "styleable",
)

private val tagsWithTypeAttribute = setOf("item", "add-resource")
private const val ANDROID_PREFIX = "android:"

/** Reads the children of a `resources` element: the resources they define and the references they contain. */
internal class ValuesReader(private val document: XmlDocument, private val collector: ResourceCollector) {
    private val scan = ReferenceScan(document, collector)
    private val styles = StyleReader(document, collector, scan)

    fun read(resources: XmlNode) {
        resources.children.forEach(::resource)
    }

    private fun resource(node: XmlNode) {
        val name = node.attribute("name")?.takeIf { it.value.isNotBlank() } ?: return
        val type = typeOf(node)?.takeIf { it.isNotBlank() } ?: return
        val key = ResourceKey(type, name.value.trim())
        val members = if (type == "styleable") styleableMembers(node) else emptyList()
        collector.define(ResourceDefinition(key, rangeOf(node), nameRangeOf(name), members))
        when (type) {
            "style" -> styles.read(node, key, name)
            "styleable" -> styleable(node, key)
            else -> scan.scan(node, key)
        }
    }

    private fun typeOf(node: XmlNode): String? =
        if (node.name in tagsWithTypeAttribute) node.attribute("type")?.value else typeOfTag[node.name]

    private fun rangeOf(node: XmlNode) = document.range(node.startOffset, node.endOffset)

    private fun nameRangeOf(name: XmlAttribute) = document.nameRange(name)

    private fun styleable(node: XmlNode, key: ResourceKey) {
        val attributes = node.children.filter { it.name == "attr" }
        attributes.forEach { attr -> attr.attribute("name")?.let { styleableAttr(key, attr, it) } }
    }

    private fun styleableAttr(styleable: ResourceKey, attr: XmlNode, name: XmlAttribute) {
        val target = ResourceKey("attr", name.value)
        when {
            ANDROID_PREFIX in name.value -> collector.external()
            declaresAttribute(attr) -> collector.define(ResourceDefinition(target, rangeOf(attr), nameRangeOf(name)))
            else -> collector.use(ResourceUse(styleable, target, null, nameRangeOf(name)))
        }
    }

    // An `attr` inside `declare-styleable` declares the attribute when it says what it is, else it refers to it.
    private fun declaresAttribute(attr: XmlNode) = attr.attribute("format") != null || attr.children.isNotEmpty()

    private fun styleableMembers(node: XmlNode): List<String> =
        node.children.filter { it.name == "attr" }.mapNotNull { it.attribute("name")?.value }
}
