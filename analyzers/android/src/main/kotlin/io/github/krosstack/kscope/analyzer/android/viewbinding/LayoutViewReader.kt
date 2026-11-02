package io.github.krosstack.kscope.analyzer.android.viewbinding

import io.github.krosstack.kscope.analyzer.android.layout.TOOLS_NAMESPACE
import io.github.krosstack.kscope.analyzer.android.resources.ANDROID_NAMESPACE
import io.github.krosstack.kscope.analyzer.android.resources.ReferenceToken
import io.github.krosstack.kscope.analyzer.android.resources.ReferenceTokens
import io.github.krosstack.kscope.analyzer.android.xml.XmlNode
import io.github.krosstack.kscope.analyzer.android.xml.XmlReader

private const val BYTE_ORDER_MARK = '﻿'
private const val MERGE = "merge"
private const val INCLUDE = "include"

/** Reads a layout file into the views that its binding class has fields for. */
internal class LayoutViewReader {
    private val xml = XmlReader()

    fun read(content: ByteArray): ReadLayout {
        val root = xml.parse(content.toString(Charsets.UTF_8).removePrefix("$BYTE_ORDER_MARK")).root
        return when {
            root == null -> ReadLayout.Empty
            root.attribute("viewBindingIgnore", TOOLS_NAMESPACE)?.value?.trim() == "true" -> ReadLayout.Ignored
            root.name == "layout" -> ReadLayout.DataBinding
            else -> ReadLayout.Bindable(LayoutViews(rootTypeOf(root), root.name == MERGE, viewsBelow(root)))
        }
    }

    private fun viewsBelow(node: XmlNode): List<BoundView> =
        listOfNotNull(viewOf(node)) + node.children.flatMap(::viewsBelow)

    private fun viewOf(node: XmlNode): BoundView? {
        val id = node.attribute("id", ANDROID_NAMESPACE)?.let { ReferenceTokens.parse(it.value) }
        return (id as? ReferenceToken.Resource)?.takeIf { it.key.type == "id" }
            ?.let { BoundView(it.key.name, it.declares, typeOf(node)) }
    }

    private fun typeOf(node: XmlNode): ViewType = if (node.name == INCLUDE) {
        val layout = ReferenceTokens.parse(node.attribute("layout")?.value.orEmpty())
        val key = (layout as? ReferenceToken.Resource)?.key?.takeIf { it.type == "layout" }
        key?.let { ViewType.Included(it.name) } ?: ViewType.Class(VIEW_TYPE)
    } else {
        declaredTypeOf(node)?.let { ViewType.Class(ViewTypes.of(it)) } ?: rootTypeOf(node)
    }

    private fun declaredTypeOf(node: XmlNode): String? =
        node.attribute("viewBindingType", TOOLS_NAMESPACE)?.value?.trim()?.takeIf { it.isNotEmpty() }

    private fun rootTypeOf(node: XmlNode): ViewType.Class {
        val viewClass = node.attribute("class")?.value?.trim()?.takeIf { node.name == "view" && it.isNotEmpty() }
        return ViewType.Class(viewClass ?: if (node.name == MERGE) VIEW_TYPE else ViewTypes.of(node.name))
    }
}
