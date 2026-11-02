package io.github.krosstack.kscope.analyzer.android.viewbinding

import io.github.krosstack.kscope.analyzer.android.rclass.JavaNames
import io.github.krosstack.kscope.analyzer.android.resources.ResourceKey
import io.github.krosstack.kscope.analyzer.android.resources.ResourceSymbols
import io.github.krosstack.kscope.analyzer.android.resources.symbolName
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SymbolId

/** A layout file of the module that has a binding: [name] is the layout, [sourceSet] the one that holds the file. */
internal class LayoutFile(val sourceSet: SourceSet, val name: String, val views: LayoutViews)

/** A field of a binding class; [sources] are the id resources that `@+id` defines for it. */
internal class BindingField(val name: String, val type: String, val sources: List<SymbolId>)

/**
 * The binding class of one layout, merged over all its configurations. [layoutSymbols] are the resource symbols of
 * the layout, one per source set that defines it; [skippedFields] counts the ids that have no field.
 */
internal data class BindingClass(
    val layout: String,
    val name: String,
    val rootType: String,
    val isMerge: Boolean,
    val fields: List<BindingField>,
    val layoutSymbols: List<SymbolId>,
    val skippedFields: Int,
)

/** The binding classes of one module. */
internal class BindingPlan(
    val module: Module,
    val namespace: String,
    val host: SourceSet,
    val classes: List<BindingClass>,
) {
    val packageName: String get() = "$namespace.databinding"
}

private class Entry(val file: LayoutFile, val view: BoundView)

/**
 * Works out the binding classes of a module from the layout files of all its configurations and source sets. A field
 * has the type of its views when they agree and `View` when they do not.
 */
internal class BindingPlanner(private val module: Module, private val namespace: String, private val host: SourceSet) {
    fun plan(files: List<LayoutFile>): BindingPlan {
        val layouts = files.filter { JavaNames.isIdentifier(BindingNames.classNameOf(it.name)) }
            .groupBy { it.name }.toSortedMap()
        return BindingPlan(module, namespace, host, layouts.map { (name, group) -> classOf(name, group, layouts.keys) })
    }

    private fun classOf(layout: String, group: List<LayoutFile>, known: Set<String>): BindingClass {
        val declared = fieldsOf(group, known)
        val usable = declared.filter { JavaNames.isIdentifier(it.name) }.distinctBy { it.name }
        val roots = group.map { it.views.rootType.name }.distinct()
        return BindingClass(
            layout = layout,
            name = BindingNames.classNameOf(layout),
            rootType = roots.singleOrNull() ?: VIEW_TYPE,
            isMerge = group.all { it.views.isMerge },
            fields = usable,
            layoutSymbols = group.map { idOf(it.sourceSet, ResourceKey("layout", layout)) }.distinct(),
            skippedFields = declared.size - usable.size,
        )
    }

    private fun fieldsOf(group: List<LayoutFile>, known: Set<String>): List<BindingField> =
        group.flatMap { file -> file.views.views.map { Entry(file, it) } }
            .groupBy { it.view.id }.toSortedMap()
            .map { (id, entries) -> fieldOf(id, entries, known) }

    private fun fieldOf(id: String, entries: List<Entry>, known: Set<String>): BindingField {
        val types = entries.map { typeNameOf(it.view.type, known) }.distinct()
        val defined = entries.filter { it.view.declares }.map { idOf(it.file.sourceSet, ResourceKey("id", id)) }
        return BindingField(BindingNames.fieldNameOf(id), types.singleOrNull() ?: VIEW_TYPE, defined.distinct())
    }

    private fun typeNameOf(type: ViewType, known: Set<String>): String = when (type) {
        is ViewType.Class -> type.name
        is ViewType.Included -> if (type.layout in known) bindingNameOf(type.layout) else VIEW_TYPE
    }

    private fun bindingNameOf(layout: String) = "$namespace.databinding.${BindingNames.classNameOf(layout)}"

    private fun idOf(sourceSet: SourceSet, key: ResourceKey): SymbolId =
        ResourceSymbols.idOf(module.id, sourceSet.symbolName(), key)
}
