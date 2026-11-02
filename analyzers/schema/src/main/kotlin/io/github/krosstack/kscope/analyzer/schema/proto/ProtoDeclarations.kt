package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind

private class Header(
    val id: SymbolId,
    val kind: SymbolKind,
    val name: String,
    val qualifiedName: String,
    val signature: String,
)

/** The symbols that a proto file declares: messages, fields, oneofs, enums, constants, services and rpcs. */
internal class ProtoDeclarations(private val file: ProtoFile, private val ids: ProtoIdentities) {
    fun symbols(): List<SchemaSymbol> =
        file.types.flatMap { typeSymbols(it, emptyList(), null) } + file.services.flatMap(::serviceSymbols)

    private fun typeSymbols(type: ProtoType, outer: List<String>, container: SymbolId?): List<SchemaSymbol> {
        val path = outer + type.name
        return when (type) {
            is ProtoMessage -> messageSymbols(type, path, container)
            is ProtoEnum -> enumSymbols(type, path, container)
        }
    }

    private fun messageSymbols(message: ProtoMessage, path: List<String>, container: SymbolId?): List<SchemaSymbol> {
        val id = ids.typeId(path)
        val own = made(header(id, ProtoKinds.message, path, null, "message ${message.name}"), message.place, container)
        val oneOfs = message.oneOfs.map { oneOf ->
            val memberId = ids.memberId(path, oneOf.name)
            made(header(memberId, ProtoKinds.oneOf, path, oneOf.name, "oneof ${oneOf.name}"), oneOf.place, id)
        }
        val fields = message.fields.map { field -> fieldSymbol(field, path, id) }
        return listOf(own) + oneOfs + fields + message.nested.flatMap { typeSymbols(it, path, id) }
    }

    private fun fieldSymbol(field: ProtoField, path: List<String>, container: SymbolId): SchemaSymbol {
        val label = field.label.takeIf { it != ProtoLabel.NONE }?.name?.lowercase()
        val signature = listOfNotNull(label, field.type, field.name).joinToString(" ") + " = ${field.tag}"
        val attributes = listOfNotNull(
            "proto.type" to field.type,
            "proto.tag" to field.tag.toString(),
            label?.let { "proto.label" to it },
            field.oneOf?.let { "proto.oneof" to it },
        ).toMap()
        val header = header(ids.memberId(path, field.name), ProtoKinds.field, path, field.name, signature)
        return made(header, field.place, container, attributes)
    }

    private fun enumSymbols(enumeration: ProtoEnum, path: List<String>, container: SymbolId?): List<SchemaSymbol> {
        val id = ids.typeId(path)
        val own = made(
            header(id, ProtoKinds.enumeration, path, null, "enum ${enumeration.name}"),
            enumeration.place,
            container,
        )
        val constants = enumeration.constants.map { constant ->
            val signature = "${constant.name} = ${constant.number}"
            val memberId = ids.memberId(path, constant.name)
            val header = header(memberId, ProtoKinds.enumConstant, path, constant.name, signature)
            made(header, constant.place, id)
        }
        return listOf(own) + constants
    }

    private fun serviceSymbols(service: ProtoService): List<SchemaSymbol> {
        val path = listOf(service.name)
        val id = ids.typeId(path)
        val own = made(header(id, ProtoKinds.service, path, null, "service ${service.name}"), service.place, null)
        val rpcs = service.rpcs.map { rpc ->
            val signature = "rpc ${rpc.name}(${rpc.requestType}) returns (${rpc.responseType})"
            made(header(ids.memberId(path, rpc.name), ProtoKinds.rpc, path, rpc.name, signature), rpc.place, id)
        }
        return listOf(own) + rpcs
    }

    private fun header(id: SymbolId, kind: SymbolKind, path: List<String>, member: String?, signature: String) =
        Header(id, kind, member ?: path.last(), ids.qualifiedName(path, member), signature)

    private fun made(
        header: Header,
        place: ProtoPlace,
        container: SymbolId?,
        attributes: Map<String, String> = emptyMap(),
    ) = SchemaSymbol(
        id = header.id,
        kind = header.kind,
        name = header.name,
        qualifiedName = header.qualifiedName,
        signature = header.signature,
        container = container,
        declaration = place.declaration,
        nameSpan = place.nameSpan,
        documentation = place.documentation,
        attributes = attributes,
    )
}
