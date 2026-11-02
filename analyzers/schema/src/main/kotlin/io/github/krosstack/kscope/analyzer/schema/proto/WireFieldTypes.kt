package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.model.SymbolId

private const val BYTE_STRING = "okio.ByteString"

private class Scalar(val kotlinType: String, val default: String)

private val scalars: Map<String, Scalar> = mapOf(
    "string" to Scalar("String", "\"\""),
    "bytes" to Scalar(BYTE_STRING, "$BYTE_STRING.EMPTY"),
    "bool" to Scalar("Boolean", "false"),
    "float" to Scalar("Float", "0F"),
    "double" to Scalar("Double", "0.0"),
) + listOf("int32", "sint32", "uint32", "fixed32", "sfixed32").associateWith { Scalar("Int", "0") } +
    listOf("int64", "sint64", "uint64", "fixed64", "sfixed64").associateWith { Scalar("Long", "0L") }

/**
 * The Kotlin type of a field and the value its constructor parameter defaults to, which is null for a required field.
 * [typeSource] is the proto symbol of the message or enum the type is made from, if it is one of the workspace.
 */
internal class WireFieldType(val type: String, val default: String?, val typeSource: SymbolId? = null)

/**
 * Maps proto field types to the Kotlin types of the Wire generator: scalars to their Kotlin counterparts, messages and
 * enums to the classes it makes, repeated fields to lists and map fields to maps. A singular field is null unless the
 * proto3 default value stands in for it, or the proto2 field is required.
 */
internal class WireFieldTypes(private val index: ProtoTypeIndex, private val isProto3: Boolean) {
    fun of(field: ProtoField, scope: List<String>): WireFieldType = when {
        field.isMap -> mapField(field, scope)

        field.label == ProtoLabel.REPEATED -> element(field.type, scope).let {
            WireFieldType("List<${it.type}>", "emptyList()", it.typeSource)
        }

        else -> singular(field, scope)
    }

    private fun mapField(field: ProtoField, scope: List<String>): WireFieldType {
        val arguments = field.type.removePrefix("map<").removeSuffix(">").split(',')
        val (key, value) = arguments.map { element(it.trim(), scope) }
        return WireFieldType("Map<${key.type}, ${value.type}>", "emptyMap()", value.typeSource ?: key.typeSource)
    }

    private fun singular(field: ProtoField, scope: List<String>): WireFieldType {
        val element = element(field.type, scope)
        val keepsDefault = isProto3 && field.label == ProtoLabel.NONE && field.oneOf == null && element.default != null
        return when {
            keepsDefault -> element
            field.label == ProtoLabel.REQUIRED -> WireFieldType(element.type, null, element.typeSource)
            else -> WireFieldType("${element.type}?", "null", element.typeSource)
        }
    }

    // The default of a message, or of a type that is not known, is null: those fields are never without a mark.
    private fun element(name: String, scope: List<String>): WireFieldType {
        val scalar = scalars[name]
        val reference = if (scalar == null) index.resolve(name, scope) else null
        return when {
            scalar != null -> WireFieldType(scalar.kotlinType, scalar.default)
            reference == null -> WireFieldType(name.removePrefix("."), null)
            else -> WireFieldType(reference.kotlinName, enumDefault(reference), reference.symbol)
        }
    }

    private fun enumDefault(reference: ProtoTypeRef): String? =
        reference.firstConstant?.takeIf { reference.isEnum }?.let { "${reference.kotlinName}.$it" }
}
