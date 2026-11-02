package io.github.krosstack.kscope.analyzer.schema.openapi.client

import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiType
import io.github.krosstack.kscope.analyzer.schema.stub.StubParameter
import io.github.krosstack.kscope.model.SymbolId

private const val ANY = "kotlin.Any"

/**
 * The Kotlin types of a generated client. A scalar is a Kotlin type by its name and format: a string is a `String`
 * whatever its format, an integer an `Int` or a `Long` for `int64`, a number a `BigDecimal` or a `Float` or a `Double`
 * by its format, and a boolean a `Boolean`. A list is a `List`, a reference to a schema with a model class is that
 * class, and anything else is `Any`. [models] are the symbols of the schemas that have a class.
 */
internal class ClientTypes(private val models: Map<String, SymbolId>, private val modelPackage: String) {
    fun of(type: OpenApiType): String = when (type) {
        is OpenApiType.Scalar -> scalarOf(type)
        is OpenApiType.ListOf -> "kotlin.collections.List<${of(type.items)}>"
        is OpenApiType.Reference -> modelOf(type.schema)
        OpenApiType.Unknown -> ANY
    }

    /** The symbol of the schema that [type] is generated from, if it is a model or a list of them. */
    fun sourceOf(type: OpenApiType): SymbolId? = when (type) {
        is OpenApiType.Reference -> models[type.schema]
        is OpenApiType.ListOf -> sourceOf(type.items)
        is OpenApiType.Scalar, OpenApiType.Unknown -> null
    }

    /** A required parameter is not null; any other is nullable and null unless given. */
    fun parameter(name: String, type: OpenApiType, required: Boolean, isProperty: Boolean = false) = StubParameter(
        name = name,
        type = if (required) of(type) else "${of(type)}?",
        default = if (required) null else "null",
        isProperty = isProperty,
        typeSource = sourceOf(type),
    )

    private fun modelOf(schema: String): String =
        if (schema in models) "$modelPackage.${ClientNames.upperCamel(schema)}" else ANY

    private fun scalarOf(type: OpenApiType.Scalar): String = when (type.type) {
        "string" -> "kotlin.String"
        "integer" -> if (type.format == "int64") "kotlin.Long" else "kotlin.Int"
        "number" -> numberOf(type.format)
        "boolean" -> "kotlin.Boolean"
        else -> ANY
    }

    private fun numberOf(format: String?): String = when (format) {
        "float" -> "kotlin.Float"
        "double" -> "kotlin.Double"
        else -> "java.math.BigDecimal"
    }
}
