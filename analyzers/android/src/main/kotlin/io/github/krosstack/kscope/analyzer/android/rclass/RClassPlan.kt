package io.github.krosstack.kscope.analyzer.android.rclass

import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.SourceSet

/** One field of a nested class of `R`: [sources] are the resource definitions it is generated from. */
internal class RField(val name: String, val isArray: Boolean, val sources: List<OwnedDefinition>)

/** A nested class of `R`, such as `string`, with its fields ordered by name. */
internal class RType(val name: String, val fields: List<RField>)

/**
 * The `R` class of one module. [host] is the source set whose stub declares it; [skippedNames] counts the resources
 * that have no field because their name is not a valid Java identifier.
 */
internal class RClassPlan(
    val module: Module,
    val namespace: String,
    val host: SourceSet,
    val types: List<RType>,
    val skippedNames: Int,
)

private class FieldSpec(val type: String, val name: String, val isArray: Boolean, val source: OwnedDefinition)

/** Works out the nested classes and fields of `R` the way the Android Gradle plugin names them. */
internal object RClassPlanner {
    fun plan(module: Module, namespace: String, host: SourceSet, visible: List<OwnedDefinition>): RClassPlan {
        val specs = visible.flatMap(::specsOf)
        val valid = specs.filter { JavaNames.isIdentifier(it.type) && JavaNames.isIdentifier(it.name) }
        val types = valid.groupBy { it.type }.toSortedMap().map { (type, inType) ->
            RType(type, inType.groupBy { it.name }.toSortedMap().map { (name, sameName) -> fieldOf(name, sameName) })
        }
        return RClassPlan(module, namespace, host, types, skippedNames = specs.size - valid.size)
    }

    private fun fieldOf(name: String, specs: List<FieldSpec>) =
        RField(name, specs.first().isArray, specs.map { it.source }.distinctBy { it.symbolId })

    private fun specsOf(owned: OwnedDefinition): List<FieldSpec> {
        val key = owned.definition.key
        val own = FieldSpec(key.type, key.fieldName, isArray = key.type == STYLEABLE, source = owned)
        val members = owned.definition.members.map { attribute ->
            FieldSpec(key.type, "${key.fieldName}_${attribute.replace(':', '_').replace('.', '_')}", false, owned)
        }
        return listOf(own) + members
    }

    private const val STYLEABLE = "styleable"
}
