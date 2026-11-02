package io.github.krosstack.kscope.benchmark.schema

import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.service.ScanLevel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
internal object DescriptorSchemas {
    fun document(name: String, descriptor: SerialDescriptor, level: LevelField = LevelField.NONE): JsonObject =
        SchemaBuilder().document(name, descriptor, level)
}

@OptIn(ExperimentalSerializationApi::class)
private class SchemaBuilder {
    private val definitions = linkedMapOf<String, JsonObject>()
    private val serialNames = mutableMapOf<String, String>()

    fun document(name: String, descriptor: SerialDescriptor, level: LevelField): JsonObject {
        val root = reference(descriptor)
        val key = key(descriptor)
        val shape = definitions.getValue(key)
        val own = shape.getValue("properties").jsonObject.filterKeys { level == LevelField.NONE || it != "indexLevel" }
        val properties = own + ("schemaVersion" to obj("const" to JsonPrimitive(1))) + levelProperty(level)
        val required = shape.getValue("required").jsonArray + JsonPrimitive("schemaVersion") +
            listOf(JsonPrimitive("indexLevel")).filter { level != LevelField.NONE }
        definitions[key] = JsonObject(
            shape + mapOf("properties" to JsonObject(properties), "required" to JsonArray(required.distinct())),
        )
        return JsonObject(
            root + mapOf(
                "\$schema" to JsonPrimitive("https://json-schema.org/draft/2020-12/schema"),
                "\$id" to JsonPrimitive("urn:kscope:$name:1"),
                "\$defs" to JsonObject(definitions.toSortedMap()),
            ),
        )
    }

    private fun levelProperty(level: LevelField): Map<String, JsonObject> {
        val names = JsonArray(ScanLevel.wireNames.map(::JsonPrimitive))
        val named = JsonObject(type("string") + ("enum" to names))
        return when (level) {
            LevelField.NONE -> emptyMap()
            LevelField.REQUIRED -> mapOf("indexLevel" to named)
            LevelField.NULLABLE -> mapOf("indexLevel" to obj("anyOf" to JsonArray(listOf(named, type("null")))))
        }
    }

    private fun schema(descriptor: SerialDescriptor): JsonObject {
        val value = if (descriptor.isInline) schema(descriptor.getElementDescriptor(0)) else shape(descriptor)
        val name = descriptor.serialName.removeSuffix("?")
        val constrained = if (name == TokenCount.serializer().descriptor.serialName) {
            JsonObject(value + ("minimum" to JsonPrimitive(0)))
        } else {
            value
        }
        return if (descriptor.isNullable) obj("anyOf" to JsonArray(listOf(constrained, type("null")))) else constrained
    }

    private fun shape(descriptor: SerialDescriptor): JsonObject = when (descriptor.kind) {
        PrimitiveKind.STRING, PrimitiveKind.CHAR -> type("string")

        PrimitiveKind.BOOLEAN -> type("boolean")

        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> type("integer")

        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> type("number")

        SerialKind.ENUM -> {
            val names = (0 until descriptor.elementsCount).map { JsonPrimitive(descriptor.getElementName(it)) }
            JsonObject(type("string") + ("enum" to JsonArray(names)))
        }

        StructureKind.LIST -> JsonObject(type("array") + ("items" to schema(descriptor.getElementDescriptor(0))))

        StructureKind.MAP -> JsonObject(
            type("object") + ("additionalProperties" to schema(descriptor.getElementDescriptor(1))),
        )

        StructureKind.CLASS, StructureKind.OBJECT, PolymorphicKind.SEALED -> reference(descriptor)

        else -> error("Unsupported schema descriptor: ${descriptor.serialName}")
    }

    private fun reference(descriptor: SerialDescriptor): JsonObject {
        val key = key(descriptor)
        if (key !in definitions) {
            definitions[key] = JsonObject(emptyMap())
            definitions[key] = if (descriptor.kind == PolymorphicKind.SEALED) {
                sealedSchema(descriptor)
            } else {
                fields(descriptor)
            }
        }
        return obj("\$ref" to JsonPrimitive("#/\$defs/$key"))
    }

    private fun fields(descriptor: SerialDescriptor, discriminator: String? = null): JsonObject {
        val properties = (0 until descriptor.elementsCount).associate { index ->
            descriptor.getElementName(index) to schema(descriptor.getElementDescriptor(index))
        }.toMutableMap()
        val required = (0 until descriptor.elementsCount).filterNot(descriptor::isElementOptional)
            .map { descriptor.getElementName(it) }.toMutableList()
        if (discriminator != null) {
            properties["type"] = obj("const" to JsonPrimitive(discriminator))
            required.add("type")
        }
        return JsonObject(
            type("object") + mapOf(
                "properties" to JsonObject(properties.toSortedMap()),
                "required" to JsonArray(required.distinct().map(::JsonPrimitive)),
                "additionalProperties" to JsonPrimitive(false),
            ),
        )
    }

    private fun sealedSchema(descriptor: SerialDescriptor): JsonObject {
        val values = descriptor.getElementDescriptor(1)
        val alternatives = (0 until values.elementsCount).map { index ->
            val subtype = values.getElementDescriptor(index)
            fields(subtype, subtype.serialName)
        }
        return obj("oneOf" to JsonArray(alternatives))
    }

    private fun key(descriptor: SerialDescriptor): String {
        val name = descriptor.serialName.removeSuffix("?")
        val key = name.replace(Regex("[^A-Za-z0-9_]"), "_")
        val previous = serialNames.putIfAbsent(key, name)
        require(previous == null || previous == name) { "Conflicting serialized names: $previous and $name" }
        return key
    }

    private fun type(name: String) = obj("type" to JsonPrimitive(name))

    private fun obj(vararg entries: Pair<String, JsonElement>) = JsonObject(entries.toMap())
}
