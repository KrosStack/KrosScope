package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.stub.StubClass
import io.github.krosstack.kscope.analyzer.schema.stub.StubEntry
import io.github.krosstack.kscope.analyzer.schema.stub.StubFile
import io.github.krosstack.kscope.analyzer.schema.stub.StubForm
import io.github.krosstack.kscope.analyzer.schema.stub.StubFunction
import io.github.krosstack.kscope.analyzer.schema.stub.StubMember
import io.github.krosstack.kscope.analyzer.schema.stub.StubParameter
import io.github.krosstack.kscope.analyzer.schema.stub.StubProperty
import io.github.krosstack.kscope.analyzer.schema.stub.StubTruth
import io.github.krosstack.kscope.analyzer.schema.stub.StubTypes

private const val BYTE_STRING = "okio.ByteString"
private const val ADAPTER = "ADAPTER"

/**
 * The Kotlin classes that the Wire generator makes for a proto file, by its naming rules: a class per message with a
 * constructor parameter per field, a `copy` function and a companion with the `ADAPTER`, and an enum class per enum.
 * Field names are kept as written in the proto file. Each declaration points at the proto symbol it comes from.
 */
internal class WireKotlinPlan(private val file: ProtoFile, private val ids: ProtoIdentities, types: ProtoTypeIndex) {
    private val fieldTypes = WireFieldTypes(types, file.isProto3)
    private val packageScope = file.packageName.split('.').filter { it.isNotEmpty() }

    fun stub(): StubFile = StubFile(file.kotlinPackage, file.types.map { classOf(it, emptyList()) })

    private fun classOf(type: ProtoType, outer: List<String>): StubClass = when (type) {
        is ProtoMessage -> messageClass(type, outer + type.name)
        is ProtoEnum -> enumClass(type, outer + type.name)
    }

    private fun messageClass(message: ProtoMessage, path: List<String>): StubClass {
        val parameters = message.fields.map { parameterOf(it, path) }
        val unknownFields = StubParameter("unknownFields", BYTE_STRING, "$BYTE_STRING.EMPTY")
        val copyParameters = (parameters + unknownFields).map {
            it.copy(default = "this.${StubTypes.written(it.name)}", isProperty = false, truth = null)
        }
        val copy = StubFunction(name = "copy", parameters = copyParameters, returnType = message.name)
        val companion = companionOf(listOf(adapterOf(message.name)))
        return StubClass(
            name = message.name,
            form = StubForm.CLASS,
            constructor = parameters + unknownFields,
            superTypes = "com.squareup.wire.Message<${message.name}, Nothing>($ADAPTER, unknownFields)",
            members = listOf(copy),
            nested = message.nested.map { classOf(it, path) } + companion,
            truth = StubTruth(ids.typeId(path), direct = true),
        )
    }

    private fun parameterOf(field: ProtoField, path: List<String>): StubParameter {
        val type = fieldTypes.of(field, packageScope + path)
        return StubParameter(
            name = field.name,
            type = type.type,
            default = type.default,
            isProperty = true,
            truth = StubTruth(ids.memberId(path, field.name), direct = true),
            typeSource = type.typeSource,
        )
    }

    private fun enumClass(enumeration: ProtoEnum, path: List<String>): StubClass {
        val fromValue = StubFunction(
            name = "fromValue",
            parameters = listOf(StubParameter("value", "Int")),
            returnType = "${enumeration.name}?",
        )
        return StubClass(
            name = enumeration.name,
            form = StubForm.ENUM_CLASS,
            constructor = listOf(StubParameter("value", "Int", isProperty = true, isOverride = true)),
            superTypes = "com.squareup.wire.WireEnum",
            entries = enumeration.constants.map {
                StubEntry(it.name, it.number.toString(), StubTruth(ids.memberId(path, it.name), direct = true))
            },
            nested = listOf(companionOf(listOf(adapterOf(enumeration.name), fromValue))),
            truth = StubTruth(ids.typeId(path), direct = true),
        )
    }

    private fun adapterOf(typeName: String) = StubProperty(ADAPTER, "com.squareup.wire.ProtoAdapter<$typeName>")

    private fun companionOf(members: List<StubMember>) =
        StubClass(name = "Companion", form = StubForm.COMPANION, members = members)
}
